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

    var pivotOffset = 0.0
    val pivotEncoder = DutyCycleEncoder(DigitalSensors.INTAKE_PIVOT)
    var pivotAngle : Double
        get() = (((pivotEncoder.get() - 0.587) / 0.256) * 90.0) + pivotOffset
        set(value) {
            pivotOffset = 0.0
            pivotOffset = value - pivotAngle
        }
    val pivotPDController = PDController(0.067/*0.30*/, 0.0)
    var pivotPDEnable = false
    var pivotSetpoint = 94.0
        get() = pivotSetpointEntry.getDouble(94.0)
        set(value) {
            field = value.coerceIn(-3.0, 94.0)
            pivotSetpointEntry.setDouble(field)
        }


    val INTAKE_POWER = 0.8

//    val button = DigitalInput(9)
    var blue = 0

    init {
        intakePivotMotor.config(20) {
//            feedbackCoefficient = 360.0 / 2048.0 / 44.0  // degrees in a rotation, ticks per rotation, gear reduction (44:1 reduction)
            brakeMode()
//            setRawOffsetConfig(analogAngle)
//            inverted(true)
//            setSensorPhase(false)
//            pid {
//                p(0.000002)
////                f(0.02)
////                    d(0.0000025)
//            }
        }
        intakeMotor.config {
            coastMode()
//            inverted(true)
        }

        //intakePivotMotor.position = pivotAngle.asDegrees


        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                currentEntry.setDouble(intakePivotMotor.current)  // intakeMotor.current)
                pivotEntry.setDouble(pivotAngle) // intakePivotMotor.position)
                if (pivotPDEnable) {
                    val power = pivotPDController.update(pivotSetpoint - pivotAngle)
                    setIntakePivotPower(power)
                }
            }
        }
    }

//    val ballIsStaged: Boolean
//        get() = !button.get()

    fun setIntakePower(power: Double) {
        intakeMotor.setPercentOutput(power)
    }

    fun setIntakePivotPower(power: Double) {
        intakePivotMotor.setPercentOutput(power)
    }

    suspend fun toggleExtend() {
        setExtend((intakePivotMotor.position-20).absoluteValue>2.0 )
    }

    suspend fun setExtend(state: Boolean) {
        println("setExtend")
        if (state) {
            setIntakePower(0.0)
            changeAngle(92.0)
        } else {
            setIntakePower(INTAKE_POWER)
            changeAngle(14.0)
        }
    }

    suspend fun changeAngle(angle: Double) {
        val angleCurve = MotionCurve()
        angleCurve.storeValue(0.0, pivotAngle)
        angleCurve.storeValue(1.0, angle)
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
//            currentEntry.setDouble(intakePivotMotor.closedLoopError)
            println(".")
        }
    //    print(":)")
//        if (ballIsStaged) {
//            setIntakePower(INTAKE_POWER)
//        } else {
//            setIntakePower(0.5 * INTAKE_POWER)
//        }
    }
}