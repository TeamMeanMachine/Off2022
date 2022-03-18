package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc2022.Drive.gyro
import org.team2471.frc2022.Drive.heading
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.halt
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.input.Controller
import org.team2471.frc.lib.math.Vector2
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.*
import kotlin.math.*

@OptIn(DelicateCoroutinesApi::class)
object Limelight : Subsystem("Front Limelight") {
    private val combinedTable = NetworkTableInstance.getDefault().getTable("limelight")
    private val frontTable = NetworkTableInstance.getDefault().getTable("limelight-front")
    private val backTable = NetworkTableInstance.getDefault().getTable("limelight-back")
//    private val thresholdTable = frontTable.getSubTable("thresholds")
    private val frontXEntry = frontTable.getEntry("tx")
    private val backXEntry = backTable.getEntry("tx")
    private val frontYEntry = frontTable.getEntry("ty")
    private val backYEntry = backTable.getEntry("ty")
    private val areaEntry = frontTable.getEntry("ta")
    private var camModeEntry = frontTable.getEntry("camMode")
    private val frontLedModeEntry = frontTable.getEntry("ledMode")
    private val backLedModeEntry = backTable.getEntry("ledMode")
    private val frontTargetValidEntry = frontTable.getEntry("tv")
    private val backTargetValidEntry = backTable.getEntry("tv")

    private val currentPipelineEntry = frontTable.getEntry("getpipe")
//    private val frontCurrentPipelineEntry = frontTable.getEntry("getpipe")
//    private val backCurrentPipelineEntry = backTable.getEntry("getpipe")
    private val setPipelineEntry = frontTable.getEntry("pipeline")
//    private val frontSetPipelineEntry = frontTable.getEntry("pipeline")
//    private val backSetPipelineEntry = backTable.getEntry("pipeline")
    private val heightToDistance = MotionCurve()
    private var distanceEntry = combinedTable.getEntry("Distance")
    private var positionXEntry = combinedTable.getEntry("PositionX")
    private var positionYEntry = combinedTable.getEntry("PositionY")
    private var aimErrorEntry = combinedTable.getEntry("Aim Error")

    private var angleOffsetEntry = Limelight.frontTable.getEntry("Angle Offset Entry")

    val useFrontLimelight: Boolean
    get() {
        var angleFromCenter = Drive.position.angle.radians
        var isFacingShooter = (angleFromCenter - heading).wrap().asDegrees.absoluteValue >= 90.0  //if the robot is facing toward (angleFromCenter opposite from heading), don't use front
//        println("isFacingShooter: $isFacingShooter   heading: ${heading.asDegrees.roundToInt()}    angleFromCenter: ${angleFromCenter.asDegrees.roundToInt()}     x: ${Drive.position.x.roundToInt()}     y: ${Drive.position.y.roundToInt()}")
//        return isFacingShooter     //do this or add other inputs like hasValidTarget
        return if (frontLedEnabled && backLedEnabled) {
            isFacingShooter
        } else if (frontLedEnabled) {
            true
        } else {
            false
        }
    }

    val distance: Length
        get() = (9.0.feet - 32.0.inches) / (34.0.degrees + xTranslation.degrees).tan()

    private val tempPIDTable = NetworkTableInstance.getDefault().getTable("fklsdajklfjsadlk;")

    private val rotationPEntry = tempPIDTable.getEntry("Rotation P").apply {
        setPersistent()
        setDefaultDouble(0.012)
    }

    private val rotationDEntry = tempPIDTable.getEntry("Rotation D").apply {
        setPersistent()
        setDefaultDouble(0.1)
    }

    private val useAutoPlaceEntry = frontTable.getEntry("Use Auto Place").apply {
        setPersistent()
        setDefaultBoolean(true)
    }

    var angleOffset: Double = 0.0
        get() = angleOffsetEntry.getDouble(0.0)
        set(value) {
            field = value
            angleOffsetEntry.setDouble(value)
        }

    val position: Vector2
        get() = Vector2(0.0, 0.0) - Vector2(
            (distance.asFeet * (heading - xTranslation.degrees).sin()),
            (distance.asFeet * (heading - xTranslation.degrees).cos())
        )

    val targetAngle: Angle
        get() {
            return -gyro.angle.degrees + xTranslation.degrees
        } //verify that this changes? or is reasonablej

    val targetPoint
        get() = Vector2(
            distance.asFeet * sin(targetAngle.asRadians),
            distance.asFeet * cos(targetAngle.asRadians)
        ) + Drive.position


    var backLedEnabled = true
        set(value) {
            field = value
//            ledModeEntry.setDouble(if (value) 0.0 else 1.0)
            backLedModeEntry.setDouble(if (value) 0.0 else 1.0)
        }

    var frontLedEnabled = true
        set(value) {
            field = value
            frontLedModeEntry.setDouble(if (value) 0.0 else 1.0)
        }


    val xTranslation
        get() = if (useFrontLimelight) frontXEntry.getDouble(0.0) else -backXEntry.getDouble(0.0)

    val yTranslation
        get() = if (useFrontLimelight) frontYEntry.getDouble(0.0) else -backYEntry.getDouble(0.0)

    val area
        get() = areaEntry.getDouble(0.0)

    val rotationP
        get() = rotationPEntry.getDouble(0.012)

    val rotationD
        get() = rotationDEntry.getDouble(0.1)

    var hasValidFrontTarget = false
        get() {
            return frontTargetValidEntry.getDouble(0.0) == 1.0
        }

    var hasValidBackTarget = false
        get() {
            return backTargetValidEntry.getDouble(0.0) == 1.0
        }

    var hasValidTarget = false
        get() = frontTargetValidEntry.getDouble(0.0) == 1.0 || backTargetValidEntry.getDouble(0.0) == 1.0



    var pipeline = 0.0
        get() = currentPipelineEntry.getDouble(0.0)
        set(value) {
            setPipelineEntry.setDouble(value)
            field = value
        }

    val aimError: Double
        get() = -yTranslation + Limelight.angleOffset // + parallax.asDegrees


    fun leftAngleOffset() {
        Limelight.angleOffset -= 0.1
    }

    fun rightAngleOffset() {
        Limelight.angleOffset += 0.1
    }


    init {
        backLedEnabled = false
        frontLedEnabled = false
        heightToDistance.storeValue(33.0, 3.0)
        heightToDistance.storeValue(22.0, 7.2)
        heightToDistance.storeValue(9.6, 11.5)
        heightToDistance.storeValue(-4.1, 22.2)
        heightToDistance.storeValue(-20.0, 35.0)

        distanceEntry = combinedTable.getEntry("Distance")
        positionXEntry = combinedTable.getEntry("PositionX")
        positionYEntry = combinedTable.getEntry("PositionY")

        //        var i = -4.1
        //        while (i < 22.5) {
        //            val tmpDistance = heightToDistance.getValue(i).feet
        //            //println("$i, ${tmpDistance.asFeet}")
        //            i += 0.5
        //        }
        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                distanceEntry.setDouble(distance.asFeet)
                val savePosition = position
                positionXEntry.setDouble(savePosition.x)
                positionYEntry.setDouble(savePosition.y)
                aimErrorEntry.setDouble(aimError)

                var leftPressed = false
                var rightPressed = false

                if (OI.operatorController.dPad == Controller.Direction.LEFT) {
                    leftPressed = true
                }

                if (OI.operatorController.dPad == Controller.Direction.RIGHT) {
                    rightPressed = true
                }

                if (OI.operatorController.dPad != Controller.Direction.LEFT && leftPressed) {
                    //  leftPressed = false
                    leftAngleOffset()
                }

                if (OI.operatorController.dPad != Controller.Direction.RIGHT && rightPressed) {
                    //  rightPressed = false
                    rightAngleOffset()
                }
            }
        }
    }

//    fun startUp() {
//        distanceEntry = combinedTable.getEntry("Distance")
//        positionXEntry = combinedTable.getEntry("PositionX")
//        positionYEntry = combinedTable.getEntry("PositionY")
//
//        ledEnabled = true
//
//        GlobalScope.launch(MeanlibDispatcher) {
//            periodic {
//                distanceEntry.setDouble(distance.asFeet)
//                val savePosition = position
//                positionXEntry.setDouble(savePosition.x)
//                positionYEntry.setDouble(savePosition.y)
//                aimErrorEntry.setDouble(aimError)
//            }
//        }
//    }

    override suspend fun default() {
        backLedEnabled = false
        halt()
    }

    override fun reset() {
    }

}


//suspend fun visionDrive() = use(Drive, FrontLimelight, name = "Vision Drive") {
//    val timer = Timer()
//    var prevTargetHeading = FrontLimelight.targetAngle
//    var prevTargetPoint = FrontLimelight.targetPoint
//    var prevTime = 0.0
//    timer.start()
//    val rotationPDController = PDController(rotationP, rotationD)
//    periodic {
//        val t = timer.get()
//        val dt = t - prevTime
//
//        // position error
//        val targetPoint = FrontLimelight.targetPoint * 0.5 + prevTargetPoint * 0.5
//        val positionError = targetPoint - Drive.position
//        prevTargetPoint = targetPoint
//
//        val robotHeading = heading
//        val targetHeading = if (BackLimelight.hasValidTarget) positionError.angle.radians else prevTargetHeading
//        val headingError = (targetHeading - robotHeading).wrap()
//        prevTargetHeading = targetHeading
//
//        val turnControl = rotationPDController.update(headingError.asDegrees )
//
//        // send it
//
//
//        Drive.drive(
//            OI.driveTranslation,
//            OI.driveRotation + turnControl,
//            SmartDashboard.getBoolean("Use Gyro", true) && !DriverStation.getInstance().isAutonomous)
//    }
//}