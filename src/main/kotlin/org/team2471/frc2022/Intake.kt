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

    var pivotOffset = if (isCompBotIHateEverything) 18.2 else -278.8
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
    val PIVOT_INTAKE = if (isCompBotIHateEverything) 12.0 else 20.5
    const val PIVOT_TOP = 103.0


//    val button = DigitalInput(9)
    var blue = 0

    init {
        intakePivotMotor.config(20) {
            feedbackCoefficient =
                360.0 / 2048.0 / 87.1875 * 90.0 / 83.0 //degrees in a rotation, ticks per rotation, gear reduction (44:1 reduction)
            brakeMode()
            pid {
                p(0.0000015)
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
                    println("Encoder failure. Running against hard stop at $targetAngle")
                    intakePivotMotor.setPercentOutput(if (targetAngle == PIVOT_BOTTOM) 0.2 else -0.2)
                    if (intakePivotMotor.current > 50.0) {
                        intakePivotMotor.setPercentOutput(0.0)
                        intakePivotMotor.setRawOffset(targetAngle.degrees)
                        pivotSetpoint = targetAngle
                        stop()
                    }
                }
            }
        }
        println(" Setpoint = $pivotSetpoint pivot angle = $pivotAngle  motor position = ${intakePivotMotor.position}")
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
        println("${((pivotAngle - angle).absoluteValue / 6.0)}")
        angleCurve.storeValue(0.0, pivotAngle)
//        angleCurve.storeValue((pivotAngle - angle).absoluteValue / 90.0, angle)
        angleCurve.storeValue((pivotAngle - angle).absoluteValue / 6.0, angle)
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
        }
    //    print(":)")
//        if (ballIsStaged) {
//            setIntakePower(INTAKE_POWER)
//        } else {
//            setIntakePower(0.5 * INTAKE_POWER)
//        }
    }


}
