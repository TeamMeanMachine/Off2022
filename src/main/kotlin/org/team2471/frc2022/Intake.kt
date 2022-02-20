package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.Timer

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.Angle
import org.team2471.frc.lib.units.degrees
import kotlin.math.absoluteValue



object Intake : Subsystem("Intake") {

    val intakeMotor = MotorController(TalonID(Talons.INTAKE_FEED), TalonID(Talons.INTAKE_FEED_TWO))
    val intakePivotMotor = MotorController(FalconID(Falcons.INTAKE_PIVOT))

    private val table = NetworkTableInstance.getDefault().getTable(Intake.name)
    val currentEntry = table.getEntry("Current")
    val angleEntry = table.getEntry("Angle")

    val pivotEncoder = DutyCycleEncoder(DigitalSensors.INTAKE_PIVOT)

    val INTAKE_POWER = 1.0

    val button = DigitalInput(9)
    var blue = 0

    init {
        intakePivotMotor.config(20) {
            feedbackCoefficient = 360.0 / 2048.0 / 44.0  // degrees in a rotation, ticks per rotation, gear reduction (44:1 reduction)
            brakeMode()
//            setRawOffsetConfig(analogAngle)
//            inverted(true)
//            setSensorPhase(false)
            pid {
                p(0.0000002)
//                f(0.02)
//                    d(0.0000025)
            }
        }
        intakeMotor.config {
            coastMode()
//            inverted(true)
        }

//        GlobalScope.launch(MeanlibDispatcher) {
//            try {
//                println("reinitializing pivot motor position")
//                periodic {
//                    setIntakePivotPower(0.2)
//                    if (intakePivotMotor.current > 60.0) {
//                        stop()
//                    }
//                }
//            }
//            finally {
//                println("finally has been reached")
//                setIntakePivotPower(0.0)
//                intakePivotMotor.position = 103.0
//            }
//        }

        intakePivotMotor.position = pivotAngle.asDegrees


        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                currentEntry.setDouble(Shooter.shootingMotor.current)  // intakeMotor.current)
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

    suspend fun toggleExtend() {
        setExtend((intakePivotMotor.position-20).absoluteValue>2.0 )
    }

    suspend fun setExtend(state: Boolean) {
        println("setExtend")
        if (state) {
            setIntakePower(0.0)
            changeAngle(98.0)
        } else {
            // setIntakePower(INTAKE_POWER)
            changeAngle(14.0)
        }
    }

    suspend fun changeAngle(angle: Double) {
        val angleCurve = MotionCurve()
        angleCurve.storeValue(0.0, intakePivotMotor.position)
        angleCurve.storeValue(0.5, angle)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
//            intakePivotMotor.setPositionSetpoint(angleCurve.getValue(t))
            if (t >= angleCurve.length) {
                stop()
            }
        }
    }

    val pivotAngle : Angle
    get() = (pivotEncoder.get()+56.0).degrees

    override suspend fun default() {
        periodic {
//            currentEntry.setDouble(intakePivotMotor.closedLoopError)
            angleEntry.setDouble(pivotAngle.asDegrees) // intakePivotMotor.position)
        }
    //    print(":)")
//        if (ballIsStaged) {
//            setIntakePower(INTAKE_POWER)
//        } else {
//            setIntakePower(0.5 * INTAKE_POWER)
//        }
    }
}