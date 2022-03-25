package org.team2471.frc2022

import edu.wpi.first.math.geometry.Pose2d
import edu.wpi.first.math.geometry.Rotation2d
import edu.wpi.first.math.geometry.Transform2d
import edu.wpi.first.math.geometry.Translation2d
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.*
import edu.wpi.first.wpilibj.smartdashboard.Field2d
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.control.PDConstantFController
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.*
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.math.Vector2
import org.team2471.frc.lib.math.round
import org.team2471.frc.lib.motion.following.SwerveDrive
import org.team2471.frc.lib.motion.following.drive
import org.team2471.frc.lib.motion_profiling.following.SwerveParameters
import org.team2471.frc.lib.units.*
import kotlin.math.absoluteValue

@OptIn(DelicateCoroutinesApi::class)
object Drive : Subsystem("Drive"), SwerveDrive {


    val redCargoEntry = NetworkTableInstance.getDefault().getTable("SmartDashboard").getSubTable("Radar").getEntry("RedCargo")
    val redFieldCargoEntry = NetworkTableInstance.getDefault().getTable("SmartDashboard").getSubTable("Field").getEntry("RedCargo")
    val table = NetworkTableInstance.getDefault().getTable(name)
    val navXGyroEntry = table.getEntry("NavX Gyro")
    val blueCargoEntry = NetworkTableInstance.getDefault().getTable("SmartDashboard").getSubTable("Radar").getEntry("BlueCargo")
    val blueFieldCargoEntry = NetworkTableInstance.getDefault().getTable("SmartDashboard").getSubTable("Field").getEntry("BlueCargo")

    val odometer0Entry = table.getEntry("Odometer 0")
    val odometer1Entry = table.getEntry("Odometer 1")
    val odometer2Entry = table.getEntry("Odometer 2")
    val odometer3Entry = table.getEntry("Odometer 3")

    val fieldObject = Field2d()
    val radarObject = Field2d()

    val limitingFactor : Double
        get() = if (Climb.climbIsPrepped) 0.25 else 1.0
    val fieldDimensions = Vector2(26.9375.feet.asMeters,54.0.feet.asMeters)
    val fieldCenterOffset = fieldDimensions/2.0
    /**
     * Coordinates of modules
     * **/
    override val modules: Array<SwerveDrive.Module> = arrayOf(
        Module(
            MotorController(FalconID(Falcons.DRIVE_FRONTLEFT)),
            MotorController(FalconID(Falcons.STEER_FRONTLEFT)),
            Vector2(-11.5, 14.0),
            (45.0).degrees,
            AnalogSensors.SWERVE_FRONT_LEFT,
            1.0, //0.0 is totally worn out and 1.0 is fresh new tread
            odometer0Entry.getDouble(0.0)
        ),
        Module(
            MotorController(FalconID(Falcons.DRIVE_FRONTRIGHT)),
            MotorController(FalconID(Falcons.STEER_FRONTRIGHT)),
            Vector2(11.5, 14.0),
            (135.0).degrees,
            AnalogSensors.SWERVE_FRONT_RIGHT,
            1.0, //0.0 is totally worn out and 1.0 is fresh new tread
            odometer1Entry.getDouble(0.0)
        ),
        Module(
            MotorController(FalconID(Falcons.DRIVE_BACKRIGHT)),
            MotorController(FalconID(Falcons.STEER_BACKRIGHT)),
            Vector2(11.5, -14.0),
            (-135.0).degrees,
            AnalogSensors.SWERVE_BACK_RIGHT,
            1.0, //0.0 is totally worn out and 1.0 is fresh new tread
            odometer2Entry.getDouble(0.0)
        ),
        Module(
            MotorController(FalconID(Falcons.DRIVE_BACKLEFT)),
            MotorController(FalconID(Falcons.STEER_BACKLEFT)),
            Vector2(-11.5, -14.0),
            (-45.0).degrees,
            AnalogSensors.SWERVE_BACK_LEFT,
            1.0, //0.0 is totally worn out and 1.0 is fresh new treadK
            odometer3Entry.getDouble(0.0)
        )
    )

    //    val gyro: Gyro? = null
    private var navX: NavxWrapper = NavxWrapper()
//    private var analogDevices: ADXRS450_Gyro = ADXRS450_Gyro()

    val gyro = navX
//    val gyro = navX /*if (navXGyroEntry.getBoolean(true) && navX != null) navX else if (analogDevices != null) analogDevices else meanGyro*/

    private var gyroOffset = 0.0.degrees

    override var heading: Angle
        get() = gyroOffset - (gyro.angle.degrees.wrap())
        set(value) {
            gyroOffset = value // -gyro.angle.degrees + value
            gyro.reset()
        }

    override val headingRate: AngularVelocity
        get() = -gyro.rate.degrees.perSecond

    var autoAim: Boolean = false

    override var velocity = Vector2(0.0, 0.0)

    override var position = Vector2(0.0, -12.0)

    override var robotPivot = Vector2(0.0, 0.0)

    override var headingSetpoint = 0.0.degrees

    override val parameters: SwerveParameters = SwerveParameters(
        gyroRateCorrection = 0.0,// 0.001,
        kpPosition = 0.32,
        kdPosition = 0.6,
        kPositionFeedForward = 0.0, //075,
        kpHeading = 0.008,
        kdHeading = 0.01,
        kHeadingFeedForward = 0.001
    )

    override val carpetFlow = if (DriverStation.getAlliance() == DriverStation.Alliance.Red) Vector2(0.0, 1.0) else Vector2(0.0, -1.0)
    override val kCarpet = 1.0 / 40.0 //to take out, make 0.0
    override val kTread = 0.0//5 //how much of an effect treadWear has

    val autoPDController = PDConstantFController(0.015, 0.012, 0.008) //not tuned
    val teleopPDController =  PDConstantFController(0.02, 0.02, 0.05) //0.02, 0.01, 0.05) //0.011, 0.032, 0.008)

    var aimPDController = teleopPDController // 0.006, 0.032, 0.011  // 0.012, 0.03, 0.0
    var lastError = 0.0

    init {
        println("drive init")
        initializeSteeringMotors()
        odometer0Entry.setPersistent()
        odometer1Entry.setPersistent()
        odometer2Entry.setPersistent()
        odometer3Entry.setPersistent()

        GlobalScope.launch(MeanlibDispatcher) {
            println("in drive global scope")

            val headingEntry = table.getEntry("Heading")
            val xEntry = table.getEntry("X")
            val yEntry = table.getEntry("Y")

//            val aimPEntry = table.getEntry("p")
//            val aimDEntry = table.getEntry("d")
            val aimErrorEntry = table.getEntry("Aim Error")
            val useGyroEntry = table.getEntry("Use Gyro")

            //val zeroGyroEntry = table.getEntry("Zero Gyro")

            SmartDashboard.setPersistent("Use Gyro")
            SmartDashboard.setPersistent("Gyro Type")
            SmartDashboard.putData("Field", fieldObject)
            SmartDashboard.setPersistent("Field")
            SmartDashboard.putData("Radar", radarObject)
            SmartDashboard.setPersistent("Radar")


            useGyroEntry.setBoolean(true)
            navXGyroEntry.setBoolean(false)

            val angleZeroEntry = table.getEntry("Swerve Angle 0")
            val angleOneEntry = table.getEntry("Swerve Angle 1")
            val angleTwoEntry = table.getEntry("Swerve Angle 2")
            val angleThreeEntry = table.getEntry("Swerve Angle 3")

            val autoAimEntry = table.getEntry("Auto Aim")

//            aimPEntry.setDouble(0.015)
//            aimDEntry.setDouble(0.005)
            periodic {
                val (x, y) = position
                xEntry.setDouble(x)
                yEntry.setDouble(y)
                headingEntry.setDouble(heading.asDegrees)
                aimErrorEntry.setDouble(Limelight.aimError)
                angleZeroEntry.setDouble((modules[0] as Module).analogAngle.asDegrees)
                angleOneEntry.setDouble((modules[1] as Module).analogAngle.asDegrees)
                angleTwoEntry.setDouble((modules[2] as Module).analogAngle.asDegrees)
                angleThreeEntry.setDouble((modules[3] as Module).analogAngle.asDegrees)
//               println("XPos: ${position.x.feet} yPos: ${position.y.feet}")
                fieldObject.robotPose = Pose2d(position.x.feet.asMeters+fieldCenterOffset.x, position.y.feet.asMeters+fieldCenterOffset.y, -Rotation2d((heading-90.0.degrees).asRadians))

                val redCargo = redCargoEntry.getDoubleArray(emptyArray())
                var redCargoOnField = ArrayList<Double>()

                for (i in 0..redCargo.size) {
                    if ((i + 1) % 3 != 0)
                        continue

                    val cargoWithBot = fieldObject.robotPose + Transform2d(Translation2d(redCargo[i-2], redCargo[i-1]), Rotation2d(gyro.angle.degrees.asRadians))
                    redCargoOnField.add(cargoWithBot.x)
                    redCargoOnField.add(cargoWithBot.y)
                    redCargoOnField.add(0.0)
                }

                redFieldCargoEntry.setDoubleArray(redCargoOnField.toDoubleArray())

                autoAim = autoAimEntry.getBoolean(false) || OI.driverController.a

               // println(gyro.getNavX().pitch.degrees)

//                for (moduleCount in 0..3) {
//                    val module = (modules[moduleCount] as Module)
//                    print("Module: $moduleCount analogAngle: ${round(module.analogAngle.asDegrees, 2)}  ")
//                }
//                println()
//                aimPDController.p = aimPEntry.getDouble(0.015)
//                aimPDController.d = aimDEntry.getDouble(0.005)
            }
        }
    }

    fun zeroGyro() {
        heading = 0.0.degrees
        //gyro.reset()
    }

    override suspend fun default() {
        periodic {
            var turn = 0.0
            if (OI.driveRotation.absoluteValue > 0.001) {
                turn = OI.driveRotation
            } else if (Limelight.hasValidTarget && (Shooter.shootMode || autoAim)) {
                turn = aimPDController.update(Limelight.aimError)
//                println("LimeLightAimError=${Limelight.aimError}")
            } else if ((Shooter.shootMode || autoAim)) {
                var error = (position.angle.degrees - heading).wrap()
                if (error.asDegrees.absoluteValue > 90.0) error = (error - 180.0.degrees).wrap()
                turn = aimPDController.update(error.asDegrees)
            }
//            printEncoderValues()

            headingSetpoint = OI.driverController.povDirection

            drive(
                OI.driveTranslation * limitingFactor,
                turn * limitingFactor,
                SmartDashboard.getBoolean("Use Gyro", true) && !DriverStation.isAutonomous(),
                false
            )
        }
    }

    fun autoSteer() {
        var turn = 0.0
        if (Limelight.hasValidTarget) {
            turn = aimPDController.update(Limelight.aimError)
        }
        Drive.drive(
            Vector2(0.0,0.0),
            turn,
            false
        )
    }

    fun printEncoderValues() {
        for (moduleCount in 0..3) {
            print("$moduleCount=${round((modules[moduleCount] as Module).analogAngle.asDegrees, 2)}   ")
        }
    }

    fun initializeSteeringMotors() {
        for (moduleCount in 0..3) {
            val module = (modules[moduleCount] as Module)
            module.turnMotor.setRawOffset(module.analogAngle)
            println("Module: $moduleCount analogAngle: ${module.analogAngle}")
        }
    }

    fun resetDriveMotors() {
        for (moduleCount in 0..3) {
            val module = (modules[moduleCount] as Module)
            module.driveMotor.restoreFactoryDefaults()
            println("For module $moduleCount, drive motor's factory defaults were restored.")
        }
    }

    fun resetSteeringMotors() {
        for (moduleCount in 0..3) {
            val module = (modules[moduleCount] as Module)
            module.turnMotor.restoreFactoryDefaults()
            println("For module $moduleCount, turn motor's factory defaults were restored.")
        }
    }

    fun brakeMode() {
//        for (moduleCount in 0..3) {
//            val module = (modules[moduleCount] as Module)
//            module.driveMotor.brakeMode()
//        }
    }

    fun coastMode() {
        for (moduleCount in 0..3) {
            val module = (modules[moduleCount] as Module)
            module.driveMotor.coastMode()
        }
    }

    class Module(
        val driveMotor: MotorController,
        val turnMotor: MotorController,
        override val modulePosition: Vector2,
        override val angleOffset: Angle,
        private val analogAnglePort: Int,
        override val treadWear: Double,
        override var odometer: Double
    ) : SwerveDrive.Module {
        companion object {
            private const val ANGLE_MAX = 983
            private const val ANGLE_MIN = 47

            private val P = 0.0075 //0.010
            private val D = 0.00075
        }

        override val angle: Angle
            get() = turnMotor.position.degrees

        private val analogAngleInput : AnalogInput = AnalogInput(analogAnglePort)

        val analogAngle: Angle
            get() {
                return ((((analogAngleInput.voltage - 0.0) / 5.0) * 360.0).degrees + angleOffset).wrap()
            }

        val driveCurrent: Double
            get() = driveMotor.current

        private val pdController = PDController(P, D)

        override val speed: Double
            get() = driveMotor.velocity

        val power: Double
            get() {
                return driveMotor.output
            }

        override val currDistance: Double
            get() = driveMotor.position

        override var prevDistance: Double = 0.0

        override fun zeroEncoder() {
            driveMotor.position = 0.0
        }

        override var angleSetpoint: Angle = 0.0.degrees
            set(value) = turnMotor.setPositionSetpoint((angle + (value - angle).wrap()).asDegrees)

        override fun setDrivePower(power: Double) {
            driveMotor.setPercentOutput(power)
        }

        val error: Angle
            get() = turnMotor.closedLoopError.degrees

        init {
            turnMotor.config(20) {
                // this was from lil bois bench test of swerve
                feedbackCoefficient = 360.0 / 2048.0 / 21.65  // ~111 ticks per degree // spark max-neo 360.0 / 42.0 / 19.6 // degrees per tick
                setRawOffsetConfig(analogAngle)
                inverted(true)
                setSensorPhase(false)
                pid {
                    p(0.000002)
//                    d(0.0000025)
                }
//                burnSettings()
            }

            val moduleContLimit: Int = when ((driveMotor.motorID as FalconID).value) {
                Falcons.DRIVE_FRONTLEFT -> 55
                Falcons.DRIVE_FRONTRIGHT -> 55   //
                Falcons.DRIVE_BACKRIGHT -> 65     //
                Falcons.DRIVE_BACKLEFT -> 65
                else -> 55
            }

            val modulePeakLimit: Int = when ((driveMotor.motorID as FalconID).value) {
                Falcons.DRIVE_FRONTLEFT -> 60
                Falcons.DRIVE_FRONTRIGHT -> 60    //
                Falcons.DRIVE_BACKRIGHT -> 70        //
                Falcons.DRIVE_BACKLEFT -> 70
                else -> 60
            }

            driveMotor.config {
                brakeMode()
                feedbackCoefficient = 1.0 / 2048.0 / 5.857 / 1.09 * 6.25 / 8.0 // spark max-neo 1.0 / 42.0/ 5.857 / fudge factor * 8ft test 2022
//                currentLimit(moduleContLimit, modulePeakLimit, 1)
                openLoopRamp(1.0)
//                burnSettings()
            }

            GlobalScope.launch {
                val table = NetworkTableInstance.getDefault().getTable(name)
                val pSwerveEntry = table.getEntry("Swerve P").apply {
                    setPersistent()
                    setDefaultDouble(0.0075)
                }
                val dSwerveEntry = table.getEntry("Swerve D").apply {
                    setPersistent()
                    setDefaultDouble(0.00075)
                }

            }

        }

        override fun driveWithDistance(angle: Angle, distance: Length) {
            driveMotor.setPositionSetpoint(distance.asFeet)
            val error = (angle - this.angle).wrap()
            pdController.update(error.asDegrees)
        }

        override fun stop() {
            driveMotor.stop()
            //turnMotor.stop()
        }
    }
}
