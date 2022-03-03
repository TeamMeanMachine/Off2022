package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.Timer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.radians
import kotlin.math.absoluteValue

object Climb : Subsystem("Climb") {
    val heightMotor = MotorController(FalconID(Falcons.CLIMB), FalconID(Falcons.CLIMB_TWO))
    val angleMotor = MotorController(FalconID(Falcons.CLIMB_ANGLE))
    val angleEncoder = DutyCycleEncoder(DigitalSensors.CLIMB_ANGLE)
    private val table = NetworkTableInstance.getDefault().getTable(name)

    val heightEntry = table.getEntry("Height")
    val heightSetpointEntry = table.getEntry("Height Setpoint")
    val angleEntry = table.getEntry("Angle")
    val angleSetpointEntry = table.getEntry("Angle Setpoint")

    val climbMode = true //set to false after climb auto creation
    val height: Double
        get() = heightMotor.position
    var heightSetpoint = 0.0
        get() = heightSetpointEntry.getDouble(0.0)
        set(value) {
            field = value.coerceIn(HEIGHT_BOTTOM, HEIGHT_TOP)
            heightSetpointEntry.setDouble(field)
        }

    val tuningMode = false

    const val HOLDING_ANGLE = 1.0

    const val HEIGHT_TOP = 30.0
    const val HEIGHT_VERTICAL_TOP = 26.0
    const val HEIGHT_BOTTOM = 0.0

    val angleOffset = -39.0
    val angle: Double
        get() = ((angleEncoder.get() - 0.05) * 37.0 / 0.13) + angleOffset
    var angleSetpoint = 0.0
        get() = angleSetpointEntry.getDouble(0.0)
        set(value) {
            field = value.coerceIn(-4.0, 36.0)
            angleSetpointEntry.setDouble(field)
        }
    val anglePDController = PDController(0.03, 0.0)

    init {
        heightMotor.config {
            brakeMode()
            followersInverted(false)
            feedbackCoefficient = 3.14 / 2048.0 / 9.38 * 30.0 / 25.0
            pid {
                p(0.00000002)
            }
        }
        angleMotor.config {
            brakeMode()
            inverted(true)
//            feedbackCoefficient = 360.0 / 2048.0 / 87.1875 * 90.0 / 83.0 / 3.0 * 39.0 / 26.0
//            pid {
//                p(0.00000000000002)
//            }
        }
        heightSetpointEntry.setDouble(height)
        angleSetpointEntry.setDouble(angle)
        GlobalScope.launch {
            periodic {
                heightEntry.setDouble(heightMotor.position)
                angleEntry.setDouble(angle)
            }
        }
    }

    override fun postEnable() {
        angleSetpoint = angle
        heightSetpoint = height
    }

    fun setPower(power: Double) {
        heightMotor.setPercentOutput(power)
    }

    fun angleSetPower(power: Double) {
        angleMotor.setPercentOutput(power)
    }

    fun zeroClimb() {
        heightMotor.setRawOffset(0.0.radians)
        heightSetpoint = 0.0
    }

    suspend fun changeAngle(target: Double) {
        val angleCurve = MotionCurve()
        print("climb angle currently at ${angle} ")
        print(" going to $target ")
        val distance = (angle - target).absoluteValue
        val rate = 30.0 / 1.0  // degrees per sec
        val time = distance / rate
        println("climb angle $time")
        angleCurve.storeValue(0.0, angle)
        angleCurve.storeValue(time, target)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
            angleSetpoint = angleCurve.getValue(t)
            println("${angleCurve.getValue(t)}")
            if (t >= angleCurve.length) {
                stop()
            }
        }
    }

    suspend fun changeHeight(target: Double) {
        val heightCurve = MotionCurve()
        print("climb angle currently at ${height} ")
        print("going to $target ")
        val distance = (height - target).absoluteValue
        val rate = 12.0 / 1.0  // degrees per sec
        val time = distance / rate
        println("climb height $time")
        heightCurve.storeValue(0.0, height)
        heightCurve.storeValue(time, target)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
            heightSetpoint = heightCurve.getValue(t)
            println("${heightCurve.getValue(t)}")
            if (t >= heightCurve.length) {
                stop()
            }
        }
    }

    override suspend fun default() {
        periodic {
            if (tuningMode) {
                heightMotor.setPositionSetpoint(heightSetpoint)
                val power = anglePDController.update(angleSetpoint - angle)
                angleSetPower(power)
            } else if (climbMode) {
                heightSetpoint -= OI.operatorLeftY * 0.12
                angleSetpoint += OI.operatorRightX * 0.12
            }
            if (OI.operatorLeftTrigger > 0.1 ||OI.operatorRightTrigger > 0.1) {
                setPower((OI.operatorRightTrigger - OI.operatorLeftTrigger) * 0.3)
            } else {
                heightMotor.setPositionSetpoint(heightSetpoint)
                val power = anglePDController.update(angleSetpoint - angle)
                angleSetPower(power)
            }
        }
    }


}