package org.team2471.frc2022

import com.revrobotics.ColorSensorV3
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycle
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.I2C
import edu.wpi.first.wpilibj.util.Color
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.input.Controller
import org.team2471.frc.lib.input.whenTrue
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.Angle
import org.team2471.frc.lib.units.degrees
import kotlin.math.absoluteValue


object Shooter : Subsystem("Shooter") {
    val shootingMotor = MotorController(FalconID(Falcons.SHOOTER), FalconID(Falcons.SHOOTER_TWO)) //private
    private val pitchMotor = MotorController(TalonID(Talons.PITCH))
    private val table = NetworkTableInstance.getDefault().getTable(name)
    val pitchEncoder = DutyCycleEncoder(DigitalSensors.SHOOTER_PITCH)

    private val i2cPort: I2C.Port = I2C.Port.kOnboard
    private val colorSensor = ColorSensorV3(i2cPort)
    val colorEntry = table.getEntry("Color")

    val rpmEntry = table.getEntry("RPM")
    val rpmSetpointEntry = table.getEntry("RPM Setpoint")
    val rpmErrorEntry = table.getEntry("RPM Error")
    val rpmOffsetEntry = table.getEntry("RPM Offset")
    val pitchEntry = table.getEntry("pitch")
    val pitchSetpointEntry = table.getEntry("pitch Setpoint")

    val pitchAngle: Angle
        get() = ((pitchEncoder.get() - 0.218) * 33.0 / 0.182).degrees
    val pitchPDController = PDController(0.5/30.0, 0.0)
    var pitchCurve: MotionCurve
    var rpmCurve: MotionCurve

    var color = "blue"


    init {
//        pitchMotor.setBounds(2.50, 1.55, 1.50, 1.45, 0.50)
        pitchCurve = MotionCurve()
        pitchCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        pitchCurve.storeValue(18.3, 30.0) //rpm 7000
        pitchCurve.storeValue(13.5, 20.7)
        pitchCurve.storeValue(9.0, 5.0)

        rpmCurve = MotionCurve()
        rpmCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        rpmCurve.storeValue(18.3, 4000.0) //rpm 7000
        rpmCurve.storeValue(13.5, 3000.0)
        rpmCurve.storeValue(9.0, 2000.0)

        shootingMotor.config {
            followersInverted(true)
            coastMode()
            feedbackCoefficient = 60.0 / (2048 * 0.33)
            pid {
                p(0.8e-5)
                i(0.0)//i(0.0)
                d(0.0)//d(1.5e-3) //1.5e-3  -- we tried 1.5e9 and 1.5e-9, no notable difference  // we printed values at the MotorController and the wrapper
                f(0.06)
            }
        }

        rpmSetpointEntry.setDouble(4000.0)
        pitchSetpointEntry.setDouble(10.0)

        GlobalScope.launch(MeanlibDispatcher) {
            var upPressed = false
            var downPressed = false
            rpmOffset = rpmOffsetEntry.getDouble(1600.0)

            periodic {
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)

                 pitchEntry.setDouble(pitchEncoderPosition)
                //val detectedColor: Color = m_colorSensor.color

//                println("Color = ${detectedColor.red} ${detectedColor.green} ${detectedColor.blue}")

                if (OI.operatorController.dPad == Controller.Direction.UP) {
                    upPressed = true
                } else if (OI.operatorController.dPad == Controller.Direction.DOWN) {
                    downPressed = true
                }
                if (OI.operatorController.dPad != Controller.Direction.UP && upPressed) {
                    upPressed = false
                    pitchSetpoint += 2
                    //incrementRpmOffset()
                }
                if (OI.operatorController.dPad != Controller.Direction.DOWN && downPressed) {
                    downPressed = false
                    pitchSetpoint -= 2
                    //decrementRpmOffset()
                }
                if (pitchPDEnable) {
                    val power = pitchPDController.update(pitchSetpoint - pitchEncoderPosition)
                    pitchSetPower(power)
//                    println("pitchSetPoint=$pitchSetpoint  encoderPosition = $pitchEncoderPosition  power = $power")
                }
                pitchEntry.setDouble(pitchAngle.asDegrees)
                if (colorSensor.color.red >= colorSensor.color.blue) {
                    color = "red"
                } else {
                    color = "blue"
                }
                colorEntry.setString(color)
//                println("red: ${colorSensor.configureColorSensor()}          blue: ${colorSensor.blue}")
//                println("angle = ${pitchAngle.asDegrees}")

            }
        }
    }

    var rpm: Double
        get() = shootingMotor.velocity
        set(value) = shootingMotor.setVelocitySetpoint(value)

    var rpmSetpoint: Double = 0.0
        get() {
//            if (FrontLimelight.hasValidTarget) {
//                val rpm2 = rpmCurve.getValue(FrontLimelight.distance) + rpmOffset
//                rpmSetpointEntry.setDouble(rpm2)
//                return rpm2
//            }

            return rpmSetpointEntry.getDouble(7000.0)
        }

    fun pitchSetPower(power: Double) {
        pitchMotor.setPercentOutput(power)
    }

    var pitchSetpoint = 10.0
        get() = pitchSetpointEntry.getDouble(10.0)
//             if (FrontLimelight.hasValidTarget) {
//                val pitch = pitchCurve.getValue(FrontLimelight.distance)
//                pitchSetpointEntry.setDouble(pitch)
//                return pitch
//            }

        set(value) {
            field = value.coerceIn(-31.0, 33.0)
            pitchSetpointEntry.setDouble(field)
        }

    var pitchEncoderPosition: Double
        get() =  pitchAngle.asDegrees
        set(value) {
            pitchSetpoint = value
        }


    var rpmOffset: Double = 0.0 //400.0
        set(value) {
            field = value
            rpmOffsetEntry.setDouble(value)
        }

    var pitchPDEnable = true

    fun incrementRpmOffset() {
        rpmOffset += 20.0
    }

    fun decrementRpmOffset() {
        rpmOffset -= 20.0
    }

    suspend fun resetPitchEncoder() = use(this) {
//        if (!pitchPDEnable) {
//            pitchSetPower(1.0)
//            var lastEncoderPosition = Intake.intakeMotor.position
//            var samePositionCounter = 0
//            periodic {
//                if ((lastEncoderPosition - Intake.intakeMotor.position).absoluteValue < 0.01) {
//                    samePositionCounter++
//                } else {
//                    samePositionCounter = 0
//                }
//                if (samePositionCounter > 10) {
//                    this.stop()
//                }
//                lastEncoderPosition = Intake.intakeMotor.position
//            }
//            pitchSetPower(0.0)
//            Intake.intakeMotor.position = 66.6
//            pitchPDEnable = true
//        }
    }

    var current = shootingMotor.current

    override suspend fun default() {
        periodic {
//            shootingMotor.stop()
            pitchEntry.setDouble(pitchAngle.asDegrees)
//            rpm = rpmSetpoint
//            println("rpm: $rpm    setpoint: $rpmSetpoint")
        }
    }
}