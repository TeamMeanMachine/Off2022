package org.team2471.frc2022

import com.revrobotics.ColorSensorV3
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.I2C
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
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.input.Controller
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.asFeet
import kotlin.math.absoluteValue


object Shooter : Subsystem("Shooter") {
    val tuningMode = false

    val shootingMotor = MotorController(FalconID(Falcons.SHOOTER), FalconID(Falcons.SHOOTER_TWO)) //private
    private val pitchMotor = MotorController(TalonID(Talons.PITCH))
    private val table = NetworkTableInstance.getDefault().getTable(name)
    val pitchEncoder = DutyCycleEncoder(DigitalSensors.SHOOTER_PITCH)

    private val i2cPort: I2C.Port = I2C.Port.kMXP
    private val colorSensor = ColorSensorV3(i2cPort)
    val colorEntry = table.getEntry("Color")

    val rpmEntry = table.getEntry("RPM")
    val rpmSetpointEntry = table.getEntry("RPM Setpoint")
    val rpmErrorEntry = table.getEntry("RPM Error")
    val rpmOffsetEntry = table.getEntry("RPM Offset")
    val pitchEntry = table.getEntry("pitch")
    val pitchSetpointEntry = table.getEntry("pitch Setpoint")
    val shootModeEntry = table.getEntry("Shoot Mode")

    const val PITCH_LOW = -31.0
    const val PITCH_HIGH = 35.0

    var pitchOffset = if (isCompBot) 1.3 else - 76.0
    var pitch: Double = 0.0
        get() = (pitchEncoder.get() - 0.218) * 33.0 / 0.182 + pitchOffset
        set(value) {
            pitchSetpoint = value
            field = value
        }
    var pitchSetpoint = pitch
        get() {
            if (tuningMode) {
                field = pitchSetpointEntry.getDouble(10.0)
            } else if (Limelight.hasValidFrontTarget) {
                val tempPitch = pitchCurve.getValue(Limelight.distance.asFeet)
                pitchSetpointEntry.setDouble(tempPitch)
                field = tempPitch
            } else if (Limelight.hasValidBackTarget) {
                val tempPitch = -pitchCurve.getValue(Limelight.distance.asFeet)
                pitchSetpointEntry.setDouble(tempPitch)
                field = tempPitch
            } else {
                field = pitchSetpointEntry.getDouble(10.0)
            }
            return field
        }
        set(value) {
            field = value.coerceIn(PITCH_LOW, PITCH_HIGH)
            pitchSetpointEntry.setDouble(field)
        }

    var pitchPDEnable = true
    val pitchPDController = PDController(0.02, 0.0) //0.055, 0.03) //0.06, 0.0) // d 0.1
    val pitchIsReady : Boolean
        get() {
//            println("${pitchPDEnable}     ${pitchSetpoint > PITCH_LOW}     ${pitchSetpoint < PITCH_HIGH}     ${pitchEncoder.isConnected}")
            return pitchPDEnable && pitch > (PITCH_LOW - 2.0) && pitchSetpoint < (PITCH_HIGH + 2.0) && pitchEncoder.isConnected
        }
    val pitchCurve: MotionCurve = MotionCurve()
    val rpmCurve: MotionCurve = MotionCurve()

//    val facingCenter : Boolean
//        get() {
//            if (Drive.)
//        }

    var rpmSetpoint: Double = 0.0
        get() {
            if (tuningMode) {
                field = rpmSetpointEntry.getDouble(5000.0)
            } else if (Limelight.hasValidTarget) {
                field = rpmCurve.getValue(Limelight.distance.asFeet) + rpmOffset
                rpmSetpointEntry.setDouble(field)
            } else {
                field = rpmSetpointEntry.getDouble(5000.0)
            }
            return field
        }
    var rpm: Double
        get() = shootingMotor.velocity
        set(value) {
            shootingMotor.setVelocitySetpoint(value)
        }

    var shootMode = false

    var color = "blue"


    init {
//        pitchMotor.setBounds(2.50, 1.55, 1.50, 1.45, 0.50)
        //right up against: 12.5
//        pitchCurve.storeValue(5.0, 23.0)
//        pitchCurve.storeValue(10.0, 32.0)
//        pitchCurve.storeValue(15.0, 35.0)
//        pitchCurve.storeValue(20.0, 35.0)

        // 03/05 tuned
        pitchCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        pitchCurve.storeValue(5.0, 12.0)
        pitchCurve.storeValue(10.0, 20.0)
        pitchCurve.storeValue(15.0, 31.0)
        pitchCurve.storeValue(20.0, 31.0)


        rpmCurve.setMarkBeginOrEndKeysToZeroSlope(false)
//        rpmCurve.storeValue(5.0, 3500.0)
//        rpmCurve.storeValue(10.0, 3750.0)
//        rpmCurve.storeValue(15.0, 4500.0)
//        rpmCurve.storeValue(20.0, 5500.0)
        rpmCurve.storeValue(5.0, 3200.0)
        rpmCurve.storeValue(10.0, 3500.0)
        rpmCurve.storeValue(15.0, 4050.0)
        rpmCurve.storeValue(20.0, 5000.0)

        shootingMotor.config {
            followersInverted(true)
            coastMode()
            feedbackCoefficient = 1.0 / 2048.0 * 60.0
            pid {
                p(1.3e-7)
                i(0.0)//i(0.0)
                d(0.0)//d(1.5e-3) //1.5e-3  -- we tried 1.5e9 and 1.5e-9, no notable difference  // we printed values at the MotorController and the wrapper
                f(0.0149)
            }
        }

        pitchMotor.config {
            currentLimit(10, 15, 10)
            inverted(!isCompBot)
        }

        rpmSetpointEntry.setDouble(rpmSetpoint)
//        pitchSetpointEntry.setDouble(10.0)

        GlobalScope.launch(MeanlibDispatcher) {
            var upPressed = false
            var downPressed = false
            rpmOffset = rpmOffsetEntry.getDouble(0.0)
            pitchSetpoint = pitch
            periodic {
                if (pitchIsReady) {
                    val power = pitchPDController.update(pitchSetpoint - pitch)
                    pitchSetPower(power)
//                    println("pitchPower $power")
                }
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)
                shootModeEntry.setBoolean(shootMode)

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
                    println("up. hi.")
                }
                if (OI.operatorController.dPad != Controller.Direction.DOWN && downPressed) {
                    downPressed = false
                    pitchSetpoint -= 2
                    //decrementRpmOffset()
                    println("down. hi.")
                }
                pitchEntry.setDouble(pitch)

                color = when (cargoIsRed) {
                    null -> "none"
                    true -> "red"
                    false -> "blue"
                } //+ colorSensor.proximity
                color = "${color}  red: ${colorSensor.red}"//"ColorSensor Disabled in code."
                colorEntry.setString(color)
//                println("red: ${colorSensor.configureColorSensor()}          blue: ${colorSensor.blue}")
//                println("angle = ${pitchAngle.asDegrees}")

                if (shootMode || tuningMode) {
                    rpm = rpmSetpoint
                } else {
                    rpm = 0.0
                }
            }
        }
    }

    override fun preEnable() {
        shootMode = false
    }



    val cargoIsStaged : Boolean
        get() = colorSensor.proximity > 250
    val cargoIsRed : Boolean?
        get() =  if (colorSensor.proximity < 180) null else colorSensor.color.red >= colorSensor.color.blue

    fun pitchSetPower(power: Double) {
        pitchMotor.setPercentOutput(power)
    }

    suspend fun changeAngle(angle: Double) {
        val angleCurve = MotionCurve()
        angleCurve.storeValue(0.0, pitch)
        angleCurve.storeValue((pitch - angle).absoluteValue / 30.0, angle)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
            pitch = angleCurve.getValue(t)
            if (t >= angleCurve.length) {
                stop()
            }
        }
    }

    var rpmOffset: Double = 0.0 //400.0
        set(value) {
            field = value
            rpmOffsetEntry.setDouble(value)
        }

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
            pitchEntry.setDouble(pitch)
        }
    }
}