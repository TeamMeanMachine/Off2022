package org.team2471.frc2022

import com.ctre.phoenix.motorcontrol.StatusFrame
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DutyCycleEncoder
import edu.wpi.first.wpilibj.Timer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.parallel
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.degrees
import org.team2471.frc.lib.units.radians
import kotlin.math.absoluteValue

object Climb : Subsystem("Climb") {
    val heightMotor = MotorController(FalconID(Falcons.CLIMB), FalconID(Falcons.CLIMB_TWO))
    val angleMotor = MotorController(FalconID(Falcons.CLIMB_ANGLE))
    val angleEncoder = DutyCycleEncoder(if (isCompBot) {DigitalSensors.CLIMB_ANGLE} else {7})
    private val table = NetworkTableInstance.getDefault().getTable(name)

    val heightEntry = table.getEntry("Height")
    val heightSetpointEntry = table.getEntry("Height Setpoint")
    val angleEntry = table.getEntry("Angle")
    val angleMotorEntry = table.getEntry("Angle Motor")
    val angleSetpointEntry = table.getEntry("Angle Setpoint")
    val robotRollEntry = table.getEntry("Roll")

    var climbIsPrepped = false
    var climbStage = 0
    var climbMode = false
    val height: Double
        get() = heightMotor.position
    var heightSetpoint
        get() = heightSetpointEntry.getDouble(0.0)
        set(value) {
//            field = value.coerceIn(HEIGHT_BOTTOM, HEIGHT_TOP)
            heightSetpointEntry.setDouble(value)
        }

    val tuningMode = false

    const val HOLDING_ANGLE = 1.0

    const val HEIGHT_TOP = 32.0
    const val HEIGHT_VERTICAL_TOP = 25.5
    const val HEIGHT_BOTTOM_DETACH = 8.0
    const val HEIGHT_BOTTOM = 0.0

    val ANGLE_TOP = if (isCompBot) 33.5 else 36.0
    const val ANGLE_BOTTOM = -4.0

    val roll : Double
        get() = Drive.gyro.getRoll()
    val angleOffset = if (isCompBot) -39.0 else 28.0
    val angleEncoderModifier = if (isCompBot) 1.0 else -1.0
//    val angleAbsoluteRaw : Double
//        get() = angleEncoder.absolutePosition
//    val angleRelativeRaw : Double
//        get() = angleEncoder.get()
//    val angleRelative: Double
//        get() = ((((angleEncoder.get() * angleEncoderModifier) - 0.05) * 37.0 / 0.13) + angleOffset).degrees.wrap().asDegrees
    val angle: Double
        get() = angleMotor.position
        // get() = ((((angleEncoder.absolutePosition * angleEncoderModifier) - 0.05) * 37.0 / 0.13) + angleOffset).degrees.wrap().asDegrees
    var angleSetpoint = 0.0
        get() = angleSetpointEntry.getDouble(0.0)
        set(value) {
            field = value.coerceIn(ANGLE_BOTTOM, ANGLE_TOP)
            angleSetpointEntry.setDouble(field)
        }
    val anglePDController = PDController(0.006, 0.002) //0.03, 0.0)
    val angleFeedForward = if (climbIsPrepped || tuningMode) 0.06 else 0.0
    var isAngleMotorControlled = true

    init {
        heightMotor.config {
            brakeMode()
            inverted(true)
            followersInverted(true)
            feedbackCoefficient = if (isCompBot) {3.14 / 2048.0 / 9.38 * 30.0 / 25.0} else {3.14 / 2048.0 / 9.38 * 30.0 / 27.0}
            pid {
                p(0.00000002)
            }
        }
        angleMotor.config {
            coastMode()
            inverted(true)
            feedbackCoefficient = (360.0 / 2048.0 / 87.1875 * 90.0 / 83.0 / 3.0 * (if (isCompBot) 34.0 / 40.0 else 39.0 / 26.0))
            pid {
                p(0.0000001)
            }
            setRawOffsetConfig((-4.0).degrees)
        }
        heightSetpointEntry.setDouble(height)
        angleSetpointEntry.setDouble(angle)
        setStatusFrames(false)
        GlobalScope.launch {
//            parallel ({
//                periodic {
//                    if (angleEncoder.isConnected && angle > ANGLE_BOTTOM && angle < ANGLE_TOP) {
//                        angleMotor.setRawOffset(-4.0.degrees) //angle.degrees) goodbye encoder
//                        angleSetpoint = angle
//                        println("setpoints angle $angle")
//                        this.stop()
//                    }
//                }
//            }, {
                periodic {
                    //println("absolute: ${round(angleAbsoluteRaw, 4)} relative:$angleRelativeRaw abs: $angleAbsolute rel: $angle diff = ${angleAbsolute - angle}")
                    heightEntry.setDouble(heightMotor.position)
                    angleEntry.setDouble(angle)
                    angleMotorEntry.setDouble(angleMotor.position)
                    robotRollEntry.setDouble(roll)
                }
        }
    }
    fun setStatusFrames(forClimb : Boolean = false) {
        val framePeriod_1 = if (forClimb) 10 else 100
        val framePeriod_2 = 2*framePeriod_1
        println("height statusframe1 from ${heightMotor.getStatusFramePeriod(StatusFrame.Status_1_General)} to $framePeriod_1")
        println("height statusframe2 from ${heightMotor.getStatusFramePeriod(StatusFrame.Status_2_Feedback0)} to $framePeriod_2")
        heightMotor.setStatusFramePeriod(StatusFrame.Status_1_General, framePeriod_1)
        heightMotor.setStatusFramePeriod(StatusFrame.Status_2_Feedback0, framePeriod_2)
        angleMotor.setStatusFramePeriod(StatusFrame.Status_1_General, framePeriod_1)
        angleMotor.setStatusFramePeriod(StatusFrame.Status_2_Feedback0, framePeriod_2)
    }

    override fun postEnable() {
        heightSetpoint = height
        climbMode = false
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
    fun angleChangeTime(target: Double) : Double {
        val distance = (angle - target).absoluteValue
        val rate = 30.0 / 1.0  // degrees per sec
        return distance / rate
    }

    suspend fun changePosition(current: Double, target: Double, time : Double, function: (value : Double) -> (Unit)) {
        val curve = MotionCurve()
        curve.storeValue(0.0, current)
        curve.storeValue(time, target)
        val timer = Timer()
        timer.start()
        periodic {
            val t = timer.get()
            function(curve.getValue(t))
            if (t >= curve.length) {
                stop()
            }
        }
    }

    suspend fun changeAngle(target: Double, minTime: Double = 0.0) {
        var time = angleChangeTime(target)
        if (minTime > time) {
            println("Time extended for changeAngle using minTime: $minTime")
            time = minTime
        }
        changePosition(angle, target, time) { value: Double ->
            angleSetpoint = value
            updatePositions()
        }
    }

    fun heightChangeTime(target: Double) : Double {
        val distance = (height - target).absoluteValue
        val rate = 15.0 / 1.0  // degrees per sec
        return distance / rate
    }

    suspend fun changeHeight(target: Double, minTime: Double = 0.0) {
        var time = heightChangeTime(target)
        if (minTime > time) {
            println("Time extended for changeHeight using minTime: $minTime")
            time = minTime
        }
        changePosition(height, target, time) { value: Double ->
            heightSetpoint = value
            updatePositions()
        }
    }

    fun updatePositions() {
        heightMotor.setPositionSetpoint(heightSetpoint)
        if (isAngleMotorControlled) {
            angleMotor.setPositionSetpoint(angleSetpoint, angleFeedForward * 1024.0)
//            println("motor setting angle power to ${angleMotor.output}")
        } else {
            val power = anglePDController.update(angleSetpoint - angle)
            angleSetPower(power + angleFeedForward)
            println("pdController setting angle power to ${power + angleFeedForward}")
        }
    }

    override suspend fun default() {
        periodic {
            if (tuningMode) {
                println("is tuning mode")
                updatePositions()
            } else if (OI.operatorLeftY.absoluteValue > 0.1 || OI.operatorRightY.absoluteValue > 0.1) {
                heightSetpoint -= OI.operatorLeftY * 0.45
                angleSetpoint += OI.operatorRightY * 0.2
                updatePositions()
//            } else if (Shooter.pitch > 19.0) {
//                angleSetpoint = Shooter.pitch - 21.0
//                updatePositions()
            } else {
                angleMotor.setPercentOutput(0.0)
            }
            if (OI.operatorLeftTrigger > 0.1 ||OI.operatorRightTrigger > 0.1) {
                setPower((OI.operatorLeftTrigger - OI.operatorRightTrigger) * 0.5)
            }
        }
    }


}