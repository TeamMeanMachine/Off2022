package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DigitalInput
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem

object Intake : Subsystem("Intake") {

    val intakeMotor = MotorController(FalconID(Falcons.INTAKE))
    val intakePivotMotor = MotorController(FalconID(Falcons.INTAKE_PIVOT))

    private val table = NetworkTableInstance.getDefault().getTable(Intake.name)
    val currentEntry = table.getEntry("Current")

    val INTAKE_POWER = 0.75
    val INTAKE_PIVOT_POWER = 0.4

    val button = DigitalInput(9)




    init {
        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                currentEntry.setDouble(intakeMotor.current)
            }
        }
    }

    val ballIsStaged: Boolean
        get() = !button.get()


    fun setIntakePower(power: Double) {
        intakeMotor.setPercentOutput(power)
    }

    fun setIntakePivotPower(power: Double) {
        intakePivotMotor.setPercentOutput(power)
    }

    suspend fun extendIntake(isOut: Boolean) {
        if (isOut) {
            setIntakePivotPower(INTAKE_PIVOT_POWER)
        } else {
            setIntakePivotPower(-INTAKE_PIVOT_POWER)
        }
        delay(1.0)
        setIntakePivotPower(0.0)
    }

    override suspend fun default() {
        if (ballIsStaged) {
            setIntakePower(INTAKE_POWER)
        } else {
            setIntakePower(0.5 * INTAKE_POWER)
        }
    }
}