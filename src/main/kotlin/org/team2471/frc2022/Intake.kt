package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycleEncoder

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
import org.team2471.frc.lib.units.degrees
import kotlin.math.absoluteValue
import edu.wpi.first.wpilibj.Timer as Timer


object Intake : Subsystem("Intake") {

    val intakeMotor = MotorController(TalonID(Talons.INTAKE))
    val intakePivotMotor = MotorController(FalconID(Falcons.INTAKE_PIVOT))

    private val table = NetworkTableInstance.getDefault().getTable(Intake.name)
    val currentEntry = table.getEntry("Current")
    val pivotEntry = table.getEntry("Pivot")
    val pivotSetpointEntry = table.getEntry("Pivot Setpoint")

    var pivotOffset = if (isCompBot) 9.7 else 1.4
    val pivotEncoder = DutyCycleEncoder(DigitalSensors.INTAKE_PIVOT)
    var pivotAngle : Double = 0.0
        get() = (pivotEncoder.get() - 0.1121) / 0.236 * 90.0 + pivotOffset
//        set(value) {
//            pivotOffset = 0.0
            //pivotOffset = value - pivotAngle
//        }
    val pivotPDController = PDController(0.05, 0.0)
    var pivotPDEnable = true
    var pivotSetpoint = pivotAngle
        get() = pivotSetpointEntry.getDouble(94.0)
        set(value) {
            field = value.coerceIn(PIVOT_BOTTOM, PIVOT_TOP)
            pivotSetpointEntry.setDouble(field)
        }

    const val INTAKE_POWER = 0.9

    const val PIVOT_BOTTOM = 0.0
    const val PIVOT_CATCH = 0.0
    val PIVOT_INTAKE = 20.5
    const val PIVOT_TOP = 103.0


//    val button = DigitalInput(9)
    var blue = 0

    init {
        intakePivotMotor.config(20) {
            feedbackCoefficient =
                360.0 / 2048.0 / 87.1875 * 90.0 / 83.0  // degrees in a rotation, ticks per rotation, gear reduction (44:1 reduction)
            brakeMode()
//            inverted(true)
            pid {
                p(0.0000025)
//                d(0.00000005)
            }
            currentLimit(40, 60, 10)
        }
        intakeMotor.config {
            coastMode()
        }

        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                currentEntry.setDouble(Feeder.shooterFeedMotor.current)  // intakeMotor.current)
                pivotEntry.setDouble(pivotAngle) // intakePivotMotor.position)
//                println(intakePivotMotor.position)
            }
        }
    }

    override fun preEnable() {
        GlobalScope.launch(MeanlibDispatcher) {
            val timer = Timer()
            timer.start()
            periodic {
                if (pivotEncoder.isConnected && pivotAngle > PIVOT_BOTTOM && pivotAngle < PIVOT_TOP) {
                    intakePivotMotor.setRawOffset(pivotAngle.degrees)
                    pivotSetpoint = pivotAngle
                    stop()
                }
                if (timer.get()>2.0) {
                    val targetAngle = PIVOT_BOTTOM  // use PIVOT_TOP for competition
                    val power = -0.2  // for competition  use 0.2
                    println("Encoder failure. Running against hard stop at $targetAngle")
//                    intakePivotMotor.setPercentOutput(power)
                    if (intakePivotMotor.current > 50.0) {
                        intakePivotMotor.setPercentOutput(0.0)
                        intakePivotMotor.setRawOffset(targetAngle.degrees)
                        pivotSetpoint = targetAngle
                        stop()
                    }
                }
            }
        }

        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                if (pivotPDEnable) {
//                    val power = pivotPDController.update(pivotSetpoint - pivotAngle)
//                    setIntakePivotPower(power)
                    intakePivotMotor.setPositionSetpoint(pivotSetpoint)
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

    suspend fun changeAngle(angle: Double) {
        val angleCurve = MotionCurve()
        print("angle currently at $pivotAngle ")
        print(" going to $angle ")
        val distance = (pivotAngle - angle).absoluteValue
        val rate = 30.0 / 1.0  // degrees per sec
        val time = distance / rate
        println("intake angle $time")
        angleCurve.storeValue(0.0, pivotAngle)
        angleCurve.storeValue(time, angle)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
            pivotSetpoint = angleCurve.getValue(t)
            println("${angleCurve.getValue(t)}")
            if (t >= angleCurve.length) {
                stop()
            }
        }
    }

    override suspend fun default() {
        periodic {
            currentEntry.setDouble(Shooter.shootingMotor.current)
            if (Feeder.ballIsStaged) {
                setIntakePower(0.0)
            }
        }
    //    print(":)")
//        if (ballIsStaged) {
//            setIntakePower(INTAKE_POWER)
//        } else {
//            setIntakePower(0.5 * INTAKE_POWER)
//        }
    }


}
