package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycleEncoder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.units.Angle
import org.team2471.frc.lib.units.degrees

object Climb : Subsystem("Climb") {
    val heightMotor = MotorController(FalconID(Falcons.CLIMB), FalconID(Falcons.CLIMB_TWO))
    val angleMotor = MotorController(FalconID(Falcons.CLIMB_ANGLE))
    val angleEncoder = DutyCycleEncoder(DigitalSensors.CLIMB_ANGLE)
    private val table = NetworkTableInstance.getDefault().getTable(name)

    val heightEntry = table.getEntry("Height")
    val heightSetpointEntry = table.getEntry("Height Setpoint")
    val angleEntry = table.getEntry("Angle")
    val angleSetpointEntry = table.getEntry("Angle Setpoint")

    val climbMode = true
    var heightSetpoint = 0.0
        get() = heightSetpointEntry.getDouble(0.0)
        set(value) {
            field = value.coerceIn(0.0, 30.0)
            heightSetpointEntry.setDouble(field)
        }

    val angle: Angle
        get() = ((angleEncoder.get() - 0.05) * -37.0 / 0.13).degrees
    var angleSetpoint = 0.0
        get() = angleSetpointEntry.getDouble(0.0)
        set(value) {
            field = value.coerceIn(-4.0, 36.0)
            angleSetpointEntry.setDouble(field)
        }
    val anglePDController = PDController(0.5/30.0, 0.0)
    var pitchEncoderPosition: Double
        get() =  Shooter.pitchAngle.asDegrees
        set(value) {
            Shooter.pitchSetpoint = value
        }

    init {
        heightMotor.config {
            brakeMode()
            followersInverted(false)
            feedbackCoefficient = 3.14 / 2048.0 / 9.38 * 30.0/25.0
            pid {
                p(0.00000002)
            }
        }
        angleMotor.config {
            brakeMode()
        }

        GlobalScope.launch(MeanlibDispatcher) {

            periodic {
                heightEntry.setDouble(heightMotor.position)
                angleEntry.setDouble(angle.asDegrees)

                if (climbMode) {
                    heightSetpoint -= OI.operatorLeftY * 0.12
                    angleSetpoint += OI.operatorRightX * 0.12
                    heightMotor.setPositionSetpoint(heightSetpoint)
                    val power = anglePDController.update(angleSetpoint - angle.asDegrees)
                    angleSetPower(power)
                }
            }
        }
    }

    fun setPower(power: Double) {
        heightMotor.setPercentOutput(power)
    }

    fun angleSetPower(power: Double) {
        angleMotor.setPercentOutput(power)
    }

    override suspend fun default() {
        periodic {
            angleSetPower(0.0)
        }

    }


}