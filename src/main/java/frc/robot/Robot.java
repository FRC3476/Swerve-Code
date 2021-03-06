// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.networktables.*;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.auton.TemplateAuto;
import frc.auton.guiauto.NetworkAuto;
import frc.auton.guiauto.serialization.OsUtil;
import frc.auton.guiauto.serialization.reflection.ClassInformationSender;
import frc.subsystem.BlinkinLED;
import frc.subsystem.Drive;
import frc.subsystem.RobotTracker;
import frc.utility.Controller;
import frc.utility.ControllerDriveInputs;
import frc.utility.Limelight;
import frc.utility.OrangeUtility;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * The VM is configured to automatically run this class, and to call the functions corresponding to each mode, as described in the
 * TimedRobot documentation. If you change the name of this class or the package after creating this project, you must also update
 * the build.gradle file in the project.
 */
@SuppressWarnings("ClassNamePrefixedWithPackageName")
public class Robot extends TimedRobot {
    //GUI
    NetworkTableInstance instance = NetworkTableInstance.getDefault();
    NetworkTable autoDataTable = instance.getTable("autodata");
    NetworkTableEntry autoPath = autoDataTable.getEntry("autoPath");

    NetworkTable position = autoDataTable.getSubTable("position");
    NetworkTableEntry xPos = position.getEntry("x");
    NetworkTableEntry yPos = position.getEntry("y");
    NetworkTableEntry enabled = autoDataTable.getEntry("enabled");
    NetworkTableEntry pathProcessingStatusEntry = autoDataTable.getEntry("processing");
    NetworkTableEntry pathProcessingStatusIdEntry = autoDataTable.getEntry("processingid");

    private final @NotNull Lock networkAutoLock = new ReentrantLock();
    NetworkAuto networkAuto;

    String lastAutoPath = null;

    ExecutorService deserializerExecutor = Executors.newSingleThreadExecutor();

    //Auto
    TemplateAuto selectedAuto;
    Thread autoThread;
    private static final String DEFAULT_AUTO = "Default";
    private static final String CUSTOM_AUTO = "My Auto";
    private final SendableChooser<String> autoChooser = new SendableChooser<>();

    //Subsystems
    private final RobotTracker robotTracker = RobotTracker.getInstance();
    private final Drive drive = Drive.getInstance();
    private final BlinkinLED blinkinLED = BlinkinLED.getInstance();
    private final Limelight limelight = Limelight.getInstance();

    //Inputs
    private final Controller xbox = new Controller(0);
    private final Controller stick = new Controller(1);
    private final Controller buttonPanel = new Controller(2);


    //Control loop states
    boolean limelightTakeSnapshots;

    Consumer<EntryNotification> autoPathListener = (event ->
            deserializerExecutor.execute(() -> { //Start deserializing on another thread
                        System.out.println("starting to parse autonomous");
                        //Set networktable entries for the gui notifications
                        pathProcessingStatusEntry.setDouble(1);
                        pathProcessingStatusIdEntry.setDouble(pathProcessingStatusIdEntry.getDouble(0) + 1);
                        networkAutoLock.lock();
                        try {
                            networkAuto = new NetworkAuto(); //Create the auto object which will start deserializing the json
                            // and the auto
                        } finally {
                            networkAutoLock.unlock();
                        }

                        // ready to be run
                        System.out.println("done parsing autonomous");
                        //Set networktable entries for the gui notifications
                        pathProcessingStatusEntry.setDouble(2);
                        pathProcessingStatusIdEntry.setDouble(pathProcessingStatusIdEntry.getDouble(0) + 1);
                    }
            ));


    /**
     * This function is run when the robot is first started up and should be used for any initialization code.
     */
    @Override
    public void robotInit() {
        if (autoPath.getString(null) != null) {
            autoPathListener.accept(new EntryNotification(NetworkTableInstance.getDefault(), 1, 1, "", null, 12));
        }

        autoPath.addListener(autoPathListener, EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

        autoChooser.setDefaultOption("Default Auto", DEFAULT_AUTO);
        autoChooser.addOption("My Auto", CUSTOM_AUTO);
        SmartDashboard.putData("Auto choices", autoChooser);


        startSubsystems();
        drive.resetGyro();
        OrangeUtility.sleep(50);
        robotTracker.resetPosition(new Pose2d());
    }

    /**
     * This function is called every robot packet, no matter the mode. Use this for items like diagnostics that you want ran
     * during disabled, autonomous, teleoperated and test.
     *
     * <p>This runs after the mode specific periodic functions, but before LiveWindow and
     * SmartDashboard integrated updating.
     */
    @Override
    public void robotPeriodic() {
        if (isEnabled()) {
            //Get data from the robot tracker and upload it to the robot tracker (Units must be in meters)
            xPos.setDouble(robotTracker.getPoseMeters().getX());
            yPos.setDouble(robotTracker.getPoseMeters().getY());
        }

        //Listen changes in the network auto
        if (autoPath.getString(null) != null && !autoPath.getString(null).equals(lastAutoPath)) {
            lastAutoPath = autoPath.getString(null);
            deserializerExecutor.execute(() -> { //Start deserializing on another thread
                System.out.println("start parsing autonomous");
                //Set networktable entries for the gui notifications
                pathProcessingStatusEntry.setDouble(1);
                pathProcessingStatusIdEntry.setDouble(pathProcessingStatusIdEntry.getDouble(0) + 1);

                networkAuto = new NetworkAuto(); //Create the auto object which will start deserializing the json and the auto
                // ready to be run
                System.out.println("done parsing autonomous");
                //Set networktable entries for the gui notifications
                pathProcessingStatusEntry.setDouble(2);
                pathProcessingStatusIdEntry.setDouble(pathProcessingStatusIdEntry.getDouble(0) + 1);
            });
        }

        //TODO: Debug why this does not work
        if (buttonPanel.getRisingEdge(9)) {
            limelightTakeSnapshots = !limelightTakeSnapshots;
            limelight.takeSnapshots(limelightTakeSnapshots);
            System.out.println("limelight taking snapshots " + limelightTakeSnapshots);
        }
    }

    /**
     * This autonomous (along with the chooser code above) shows how to select between different autonomous modes using the
     * dashboard. The sendable chooser code works with the Java SmartDashboard. If you prefer the LabVIEW Dashboard, remove all of
     * the chooser code and uncomment the getString line to get the auto name from the text box below the Gyro
     *
     * <p>You can add additional auto modes by adding additional comparisons to the switch structure
     * below with additional strings. If using the SendableChooser make sure to add them to the chooser code above as well.
     */
    @Override
    public void autonomousInit() {
        enabled.setBoolean(true);
        drive.configBrake();

        networkAutoLock.lock();
        try {
            if (networkAuto == null) {
                System.out.println("Using normal autos");
                String auto = autoChooser.getSelected();
                switch (auto) {
                    //Put all your autos here
                }
            } else {
                System.out.println("Using autos from network tables");
                selectedAuto = networkAuto;
            }
        } finally {
            networkAutoLock.unlock();
        }

        assert selectedAuto != null;
        //Since autonomous objects can be reused they need to be reset them before we can reuse them again 
        selectedAuto.reset();

        //We then create a new thread to run the auto and run it
        autoThread = new Thread(selectedAuto);
        autoThread.start();
    }

    /**
     * This function is called periodically during autonomous.
     */
    @Override
    public void autonomousPeriodic() {
    }

    /**
     * This function is called once when teleop is enabled.
     */
    @Override
    public void teleopInit() {
        killAuto();
        enabled.setBoolean(true);
        startSubsystems();
        drive.resetGyro();
    }

    /**
     * This function is called periodically during operator control.
     */
    @Override
    public void teleopPeriodic() {
        xbox.update();
        stick.update();
        buttonPanel.update();
        if (xbox.getRawButton(3)) {
            //Increase the deadzone so that we drive straight
            drive.swerveDriveFieldRelative(new ControllerDriveInputs(-xbox.getRawAxis(1), -xbox.getRawAxis(0),
                    -xbox.getRawAxis(4)).applyDeadZone(0.2, 0.2, 0.2, 0.2).squareInputs());
        } else {
            drive.swerveDriveFieldRelative(new ControllerDriveInputs(-xbox.getRawAxis(1), -xbox.getRawAxis(0),
                    -xbox.getRawAxis(4)).applyDeadZone(0.05, 0.05, 0.2, 0.2).squareInputs());
        }

        if (xbox.getRisingEdge(1)) {
            drive.resetGyro();
        }

    }

    /**
     * This function is called once when the robot is disabled.
     */
    @Override
    public void disabledInit() {
        killAuto();
        enabled.setBoolean(false);
    }

    /**
     * This function is called periodically when disabled.
     */
    @Override
    public void disabledPeriodic() {
    }

    /**
     * This function is called once when test mode is enabled.
     */
    @Override
    public void testInit() {
        startSubsystems();
    }

    /**
     * This function is called periodically during test mode.
     */
    @Override
    public void testPeriodic() {
    }

    private void startSubsystems() {
        robotTracker.start();
        drive.start();
    }

    public synchronized void killAuto() {
        System.out.println("Killing Auto");
        if (selectedAuto != null) {
            assert autoThread != null;
            autoThread.interrupt();
            double nextStackTracePrint = Timer.getFPGATimestamp() + 1;
            while (!(selectedAuto.isFinished() || autoThread.getState() == Thread.State.TERMINATED)) {
                if (Timer.getFPGATimestamp() > nextStackTracePrint) {
                    Exception throwable = new Exception(
                            "Waiting for auto to die. selectedAuto.isFinished() = " + selectedAuto.isFinished() +
                                    " autoThread.getState() = " + autoThread.getState());
                    throwable.setStackTrace(autoThread.getStackTrace());
                    throwable.printStackTrace();
                    nextStackTracePrint = Timer.getFPGATimestamp() + 5;
                }


                OrangeUtility.sleep(10);
            }
            drive.stopMovement();
            drive.setTeleop();
        }
    }

    @Override
    public void simulationInit() {
        ClassInformationSender.updateReflectionInformation(
                new File(OsUtil.getUserConfigDirectory("AutoBuilder") + "/robotCodeData.json"));
    }
}
