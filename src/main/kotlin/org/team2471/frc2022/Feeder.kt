package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DriverStation
import edu.wpi.first.wpilibj.DutyCycleEncoder

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem


object Feeder : Subsystem("Feeder") {

    val shooterFeedMotor = MotorController(TalonID(Talons.SHOOTER_FEED))
    val bedFeedMotor = MotorController(TalonID(Talons.BED_FEED))

    val button = DigitalInput(DigitalSensors.FEEDER_BUTTON)
    val feedDistanceEncoder = DutyCycleEncoder(DigitalSensors.FEEDER_DISTANCE)

    private val table = NetworkTableInstance.getDefault().getTable(Feeder.name)

    val feedUseFrontLimelightEntry = table.getEntry("useFrontLimelight")
    val distanceEntry = table.getEntry("Distance")
    val stageStatusEntry = table.getEntry("Mode")
    val isClearingEntry = table.getEntry("Clearing")

    val SHOOTER_FEED_POWER = if (isCompBot) 0.7 else 1.0
    val SHOOTER_STAGE_POWER = if (isCompBot) 0.5 else 1.0

    const val BED_FEED_POWER = 0.8

    var isAuto = DriverStation.isAutonomous()
    var isClearing = false

    enum class Status {
        EMPTY,
        SINGLE_STAGED,
        DUAL_STAGED,
        ACTIVELY_SHOOTING,
        CLEARING
    }

    var currentFeedStatus : Status = Status.EMPTY
    var autoFeedMode = false

    init {
        shooterFeedMotor.config {
            brakeMode()
            inverted(true)
        }
        bedFeedMotor.config {
            inverted(!isCompBot)
        }

        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                isAuto = DriverStation.isAutonomous()

                currentFeedStatus = when {
                    Shooter.shootMode && isAuto -> Status.ACTIVELY_SHOOTING
                    Shooter.shootMode && OI.driveRightTrigger > 0.1 -> Status.ACTIVELY_SHOOTING
                    isClearing -> Status.CLEARING
                    Shooter.cargoIsStaged && cargoIsStaged -> Status.DUAL_STAGED
                    Shooter.cargoIsStaged -> Status.SINGLE_STAGED
                    else -> Status.EMPTY
                }
                stageStatusEntry.setString(currentFeedStatus.name)
                feedUseFrontLimelightEntry.setBoolean(Limelight.useFrontLimelight)
                distanceEntry.setDouble(feedDistance)
                isClearingEntry.setBoolean(isClearing)
                if (autoFeedMode) {
                    when (currentFeedStatus) {
                        Status.ACTIVELY_SHOOTING -> {
                            setBedFeedPower(BED_FEED_POWER)
                            setShooterFeedPower(SHOOTER_FEED_POWER)
                        }
                        Status.DUAL_STAGED -> {
                            setBedFeedPower(0.0)
                            setShooterFeedPower(0.0)
                        }
                        Status.SINGLE_STAGED -> {
                            setBedFeedPower(BED_FEED_POWER)
                            if (Shooter.cargoStageProximity > Shooter.PROXMITY_STAGED_MAX_SAFE) {
                                setShooterFeedPower(-0.2)
                            } else {
                                setShooterFeedPower(0.0)
                            }
                        }
                        Status.EMPTY -> {
                            setBedFeedPower(BED_FEED_POWER)
                            setShooterFeedPower(SHOOTER_STAGE_POWER)
                        }
                        Status.CLEARING -> {
                            setBedFeedPower(-BED_FEED_POWER)
                            setShooterFeedPower(-SHOOTER_FEED_POWER)
                        }
                    }
                } else if (!isAuto) {
                    if (Shooter.shootMode) {
                        setShooterFeedPower(OI.driveRightTrigger)
                        setBedFeedPower(0.0)
                    } else if (isClearing) {
                        setBedFeedPower(-BED_FEED_POWER)
                        setShooterFeedPower(-SHOOTER_FEED_POWER)
                    } else  {
                        setShooterFeedPower(0.0)
                        setBedFeedPower(0.0)
                    }
                }
            }
        }
    }

    override fun preEnable() {
        setShooterFeedPower(0.0)
    }

    val cargoIsStaged: Boolean
        get() = !button.get()

    val feedDistance: Double
        get() = -feedDistanceEncoder.absolutePosition / Math.PI * 20.0

    fun setShooterFeedPower(power: Double) {
        shooterFeedMotor.setPercentOutput(power)
    }

    fun setBedFeedPower(power: Double) {
        bedFeedMotor.setPercentOutput(power)
    }

    override suspend fun default() {
        periodic {
            feedUseFrontLimelightEntry.setBoolean(Limelight.useFrontLimelight)
        }
    }
}