package frc.auton.guiauto.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import frc.auton.TemplateAuto;
import frc.auton.guiauto.serialization.command.SendableScript;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptAutonomousStep extends AbstractAutonomousStep {

    private final SendableScript sendableScript;

    @JsonCreator
    public ScriptAutonomousStep(@JsonProperty(required = true, value = "sendableScript") SendableScript sendableScript) {
        this.sendableScript = sendableScript;
    }

    @Override
    public String toString() {
        return "ScriptAutonomousStep{" + "sendableScript='" + sendableScript + '\'' + '}';
    }

    @JsonProperty
    public SendableScript getSendableScript() {
        return sendableScript;
    }

    /**
     * Runs the script
     */
    @Override
    public void execute(TemplateAuto templateAuto,
                        List<SendableScript> scriptsToExecuteByTime,
                        List<SendableScript> scriptsToExecuteByPercent) {
        if (!templateAuto.isDead()) { //Check that our auto is still running
            if (sendableScript.getDelayType() == SendableScript.DelayType.TIME) {
                scriptsToExecuteByTime.add(sendableScript);
                return;
            }

            if (sendableScript.getDelayType() == SendableScript.DelayType.PERCENT) {
                scriptsToExecuteByPercent.add(sendableScript);
                return;
            }

            if (!sendableScript.execute()) {
                //The sendableScript failed to execute; kill the auto
                templateAuto.killSwitch();
            }
        }
    }
}