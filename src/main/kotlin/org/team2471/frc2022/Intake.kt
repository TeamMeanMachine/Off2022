package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.Timer

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.motion_profiling.MotionCurve
import kotlin.math.absoluteValue



object Intake : Subsystem("Intake") {

    val intakeMotor = MotorController(TalonID(Talons.INTAKE_FEED), TalonID(Talons.INTAKE_FEED_TWO))
    val intakePivotMotor = MotorController(FalconID(Falcons.INTAKE_PIVOT))

    private val table = NetworkTableInstance.getDefault().getTable(Intake.name)
    val currentEntry = table.getEntry("Current")
    val pivotEntry = table.getEntry("Pivot")
    val pivotSetpointEntry = table.getEntry("Pivot Setpoint")

    var pivotOffset = 140.0
    val pivotEncoder = DutyCycleEncoder(DigitalSensors.INTAKE_PIVOT)
    var pivotAngle : Double
        get() = (((pivotEncoder.get() - 0.587) / 0.256) * 90.0) + pivotOffset
        set(value) {
            pivotOffset = 0.0
            pivotOffset = value - pivotAngle
        }
    val pivotPDController = PDController(0.05, 0.0)
    var pivotPDEnable = true
    var pivotSetpoint = pivotAngle
        get() = pivotSetpointEntry.getDouble(94.0)
        set(value) {
            field = value.coerceIn(PIVOT_BOTTOM, PIVOT_TOP)
            pivotSetpointEntry.setDouble(field)
        }

    val INTAKE_POWER = 0.8

    val PIVOT_BOTTOM = -3.0
    val PIVOT_CATCH = 0.0
    val PIVOT_INTAKE = 17.0
    val PIVOT_TOP = 94.0


//    val button = DigitalInput(9)
    var blue = 0

    init {
        intakePivotMotor.config(20) {
            feedbackCoefficient = 360.0 / 2048.0 / 44.0  // degrees in a rotation, ticks per rotation, gear reduction (44:1 reduction)
            brakeMode()
            pid {
                p(0.000002)
            }
        }
        intakeMotor.config {
            coastMode()
        }

        intakePivotMotor.position = pivotAngle
        pivotSetpoint = pivotAngle

        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                currentEntry.setDouble(Feeder.feedMotor.current)  // intakeMotor.current)
                pivotEntry.setDouble(pivotAngle) // intakePivotMotor.position)
                if (pivotPDEnable) {
//                    val power = pivotPDController.update(pivotSetpoint - pivotAngle)
//                    setIntakePivotPower(power)
                    intakePivotMotor.setPositionSetpoint(pivotSetpoint)
                }
            }
        }
    }

    override fun preEnable() {
        pivotSetpoint = pivotAngle.coerceIn(0.0, 95.0)
    }

//    val ballIsStaged: Boolean
//        get() = !button.get()

    fun setIntakePower(power: Double) {
        intakeMotor.setPercentOutput(power)
    }

    fun setIntakePivotPower(power: Double) {
        intakePivotMotor.setPercentOutput(power)
    }

    suspend fun changeAngle(angle: Double) {
        val angleCurve = MotionCurve()
        angleCurve.storeValue(0.0, pivotAngle)
        angleCurve.storeValue((pivotAngle - angle).absoluteValue / 90.0, angle)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
            pivotSetpoint = angleCurve.getValue(t)
            if (t >= angleCurve.length) {
                stop()
            }
        }
    }

    override suspend fun default() {
        periodic {
            currentEntry.setDouble(Shooter.shootingMotor.current)
        }
    //    print(":)")
//        if (ballIsStaged) {
//            setIntakePower(INTAKE_POWER)
//        } else {
//            setIntakePower(0.5 * INTAKE_POWER)
//        }
    }


}
