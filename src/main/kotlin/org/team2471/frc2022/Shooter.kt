package org.team2471.frc2022

import com.revrobotics.ColorSensorV3
import edu.wpi.first.networktables.NetworkTableInstance
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
import org.team2471.frc.lib.motion_profiling.MotionCurve
import kotlin.math.absoluteValue


object Shooter : Subsystem("Shooter") {
    private val shootingMotor = MotorController(FalconID(Falcons.SHOOTER), FalconID(Falcons.SHOOTER_TWO))
    private val hoodMotor = MotorController(TalonID(Talons.HOOD))
    private val table = NetworkTableInstance.getDefault().getTable(name)
    private val i2cPort: I2C.Port = I2C.Port.kOnboard
    private val m_colorSensor = ColorSensorV3(i2cPort)


    val rpmEntry = table.getEntry("RPM")
    val rpmSetpointEntry = table.getEntry("RPM Setpoint")
    val rpmErrorEntry = table.getEntry("RPM Error")
    val rpmOffsetEntry = table.getEntry("RPM Offset")
    val hoodEntry = table.getEntry("Hood")
    val hoodSetpointEntry = table.getEntry("Hood Setpoint")

    var hoodCurve: MotionCurve

    init {
//        hoodMotor.setBounds(2.50, 1.55, 1.50, 1.45, 0.50)
        hoodCurve = MotionCurve()
        hoodCurve.setMarkBeginOrEndKeysToZeroSlope(false)
        hoodCurve.storeValue(18.3, 48.0) //rpm 7000
        hoodCurve.storeValue(13.5, 45.7)
        hoodCurve.storeValue(9.0, 37.0)

        shootingMotor.config {
        }

        rpmSetpointEntry.setDouble(7000.0)
        hoodSetpointEntry.setDouble(50.0)

        GlobalScope.launch(MeanlibDispatcher) {
            var upPressed = false
            var downPressed = false
            rpmOffset = rpmOffsetEntry.getDouble(1600.0)

            periodic {
//                println(hoodMotor.analogAngle)
//                hoodMotor.setPercentOutput( 0.25 * (OI.operatorController.leftTrigger - OI.operatorController.rightTrigger))
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)

                 hoodEntry.setDouble(hoodEncoderPosition)
                val detectedColor: Color = m_colorSensor.configureColorSensor()

                println("Color = ${detectedColor.red} ${detectedColor.green} ${detectedColor.blue}")

                if (OI.operatorController.dPad == Controller.Direction.UP) {
                    upPressed = true
                } else if (OI.operatorController.dPad == Controller.Direction.DOWN) {
                    downPressed = true
                }
                if(OI.operatorController.dPad != Controller.Direction.UP && upPressed) {
                    upPressed = false
                    incrementRpmOffset()
                }
                if(OI.operatorController.dPad != Controller.Direction.DOWN && downPressed) {
                    downPressed = false
                    decrementRpmOffset()
                }
                if (hoodPDEnable) {
                    val power = hoodPDController.update(hoodSetpoint - hoodEncoderPosition)
                    hoodSetPower(power)
//                    println("hoodSetPoint=$hoodSetpoint  encoderPosition = $hoodEncoderPosition  power = $power")
                }
            }
        }
    }

    fun hoodSetPower(power: Double) {
        hoodMotor.setPercentOutput(power)
    }

    var rpm: Double
        get() = shootingMotor.velocity
        set(value) = shootingMotor.setVelocitySetpoint(value)

    var hoodSetpoint = 45.0
        get() = hoodSetpointEntry.getDouble(45.0)

    var hoodEncoderPosition: Double
        get() = Intake.intakeMotor.position
        set(value) {
            hoodSetpoint = value.coerceIn(21.0, 66.0)
        }

    val hoodPDController = PDController(0.5, 0.0)

    var rpmSetpoint: Double = 0.0
        get() {
//            if (FrontLimelight.hasValidTarget) {
//                val rpm2 = rpmFromDistance(FrontLimelight.distance) + rpmOffset
//                rpmSetpointEntry.setDouble(rpm2)
//                return rpm2
//            } else {
//                field = rpmCurve.getValue(20.0) + rpmOffset
//                rpmSetpointEntry.setDouble(field)
//                return field
//            }

            return rpmSetpointEntry.getDouble(7000.0)
        }

    var rpmOffset: Double = 0.0 //400.0
        set(value) {
            field = value
            rpmOffsetEntry.setDouble(value)
        }

    var hoodPDEnable = false

    fun incrementRpmOffset() {
        rpmOffset += 20.0
    }

    fun decrementRpmOffset() {
        rpmOffset -= 20.0
    }

    suspend fun resetHoodEncoder() = use(this) {
        if (!hoodPDEnable) {
            hoodSetPower(1.0)
            var lastEncoderPosition = Intake.intakeMotor.position
            var samePositionCounter = 0
            periodic {
                if ((lastEncoderPosition - Intake.intakeMotor.position).absoluteValue < 0.01) {
                    samePositionCounter++
                } else {
                    samePositionCounter = 0
                }
                if (samePositionCounter > 10) {
                    this.stop()
                }
                lastEncoderPosition = Intake.intakeMotor.position
            }
            hoodSetPower(0.0)
            Intake.intakeMotor.position = 66.6
            hoodPDEnable = true
        }
    }

    var current = shootingMotor.current

    override suspend fun default() {
        periodic {
            shootingMotor.stop()
            //  hoodMotor.stop()
        }
    }
}