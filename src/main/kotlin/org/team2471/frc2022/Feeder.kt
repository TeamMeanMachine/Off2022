package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.Timer

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.motion_profiling.MotionCurve
import kotlin.math.absoluteValue



object Feeder : Subsystem("Feeder") {

    val feedMotor = MotorController(TalonID(Talons.FEED))

    private val table = NetworkTableInstance.getDefault().getTable(Intake.name)
    val currentEntry = table.getEntry("Current")
    val angleEntry = table.getEntry("Angle")

    val INTAKE_POWER = 0.7

//    val button = DigitalInput(9)
    var blue = 0



    init {
        feedMotor.config {
            brakeMode()
            inverted(true)
        }

    }

//
//    val ballIsStaged: Boolean
//        get() = !button.get()


    fun setPower(power: Double) {
        feedMotor.setPercentOutput(power)
    }


    override suspend fun default() {
        periodic {
            setPower(0.0)
        }
        //    print(":)")
//        if (ballIsStaged) {
//            setIntakePower(INTAKE_POWER)
//        } else {
//            setIntakePower(0.5 * INTAKE_POWER)
//        }
    }
}