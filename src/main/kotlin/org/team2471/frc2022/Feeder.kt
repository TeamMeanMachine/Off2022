package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DutyCycleEncoder

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.framework.use
import kotlin.math.absoluteValue


object Feeder : Subsystem("Feeder") {

    val shooterFeedMotor = MotorController(TalonID(Talons.SHOOTER_FEED))
    val bedFeedMotor = MotorController(TalonID(Talons.BED_FEED))

    val button = DigitalInput(DigitalSensors.FEEDER_BUTTON)
    val feedDistanceEncoder = DutyCycleEncoder(DigitalSensors.FEEDER_DISTANCE)
    val feedPDController = PDController(0.1, 0.0)

    private val table = NetworkTableInstance.getDefault().getTable(Feeder.name)
    val currentEntry = table.getEntry("Current")
    val angleEntry = table.getEntry("Angle")
    val feedEntry = table.getEntry("useFrontLimelight")
    val distanceEntry = table.getEntry("Distance")

    val SHOOTER_FEED_POWER = if (isCompBot) 0.7 else 1.0
    const val BED_FEED_POWER = 0.8
    const val STAGE_DISTANCE = 3.0

    var blue = 0
    var autoFeedMode = true

    init {
        shooterFeedMotor.config {
            brakeMode()
            inverted(true)
        }
        bedFeedMotor.config {
            inverted(!isCompBot)
        }
        GlobalScope.launch(MeanlibDispatcher) {
            var cargoWasStaged = false
            periodic {
//                feedEntry.setBoolean(ballIsStaged)
                feedEntry.setBoolean(Limelight.useFrontLimelight)
                distanceEntry.setDouble(feedDistance)
                if (autoFeedMode) {
                    if (Shooter.cargoIsStaged) {
                        if (!cargoWasStaged) {
                            feedDistanceEncoder.reset()
                            cargoWasStaged = true
                        }
                        val power = feedPDController.update(feedDistance - STAGE_DISTANCE)
                        setShooterFeedPower(power + OI.driveRightTrigger)
                        if (ballIsStaged) {
                            setBedFeedPower(0.0)
                        } else {
                            setBedFeedPower(BED_FEED_POWER)
                        }
                    } else {
                        setShooterFeedPower(SHOOTER_FEED_POWER)
                        setBedFeedPower(BED_FEED_POWER)
                        cargoWasStaged = false
                    }
                }
            }
        }

    }

    override fun preEnable() {
        setShooterFeedPower(0.0)
    }

//
    val ballIsStaged: Boolean
        get() = !button.get()

    val feedDistance: Double
        get() = -feedDistanceEncoder.absolutePosition / Math.PI * 20.0

    fun setShooterFeedPower(power: Double) {
        shooterFeedMotor.setPercentOutput(power)
    }

    fun setBedFeedPower(power: Double) {
        bedFeedMotor.setPercentOutput(power)
    }

    suspend fun feed(distance: Double) = use(this){
//        feedDistanceEncoder.reset()
        setShooterFeedPower(0.8)
        periodic {
//            var error = distance - feedDistance
//            val power = feedPDController.update(error)
//            setShooterFeedPower(power)
//            println("feed power $power")
            println("distance: ${Limelight.distance}    rpm: ${Shooter.rpm}    pitchSetpoint: ${Shooter.pitchSetpoint}")
            if (!Shooter.cargoIsStaged && !ballIsStaged) {
                println("stopped feed")
                stop()
            }
        }
    }

    override suspend fun default() {
        periodic {
            feedEntry.setBoolean(Limelight.useFrontLimelight)
        }
    }
}