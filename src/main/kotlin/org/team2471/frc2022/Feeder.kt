package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DigitalInput

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

    val button = DigitalInput(4)

    private val table = NetworkTableInstance.getDefault().getTable(Feeder.name)
    val currentEntry = table.getEntry("Current")
    val angleEntry = table.getEntry("Angle")
    val feedEntry = table.getEntry("Staged")

    const val SHOOTER_FEED_POWER = 0.8
    const val BED_FEED_POWER = 0.8

    var blue = 0

    init {
        shooterFeedMotor.config {
            brakeMode()
            inverted(true)
        }
        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
//                feedEntry.setBoolean(ballIsStaged)
                feedEntry.setBoolean(Intake.pivotEncoder.isConnected)
            }
        }

    }

    override fun preEnable() {
        setShooterFeedPower(0.0)
    }

//
    val ballIsStaged: Boolean
        get() = !button.get()


    fun setShooterFeedPower(power: Double) {
        shooterFeedMotor.setPercentOutput(power)
    }

    fun setBedFeedPower(power: Double) {
        bedFeedMotor.setPercentOutput(power)
    }

    override suspend fun default() {
/*
        periodic {
            if (Shooter.cargoIsStaged) {
                setShooterFeedPower(0.0 + OI.driveRightTrigger)
                println("Shooter Staged")
                if (ballIsStaged) {
                    setBedFeedPower(0.0)
                    println("Intake Staged")
                } else {
                    setBedFeedPower(BED_FEED_POWER)
                    println("Intake Powering - waiting for 2nd cargo")
                }
            } else {
                setShooterFeedPower(SHOOTER_FEED_POWER)
                setBedFeedPower(BED_FEED_POWER)
                println("Feeder Power")
            }
        }
*/
    }
}