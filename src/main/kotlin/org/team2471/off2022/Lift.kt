package org.team2471.off2022

import edu.wpi.first.networktables.NetworkTableInstance
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem

object Lift: Subsystem("Lift") {
    val liftMotor = MotorController(FalconID(Falcons.LIFT_1), FalconID(Falcons.LIFT_2))

    private val table = NetworkTableInstance.getDefault().getTable(Lift.name)
    val heightEntry = table.getEntry("Height")
    val heightSetpointEntry = table.getEntry("Height Setpoint")

    var height: Double = 0.0
        get() = 0.0 //Come back to

    var heightSetpoint: Double
        get() = heightSetpointEntry.getDouble(0.0)
        set(value) {
            heightSetpointEntry.setDouble(value)
        }

    init {
        Lift.liftMotor.config(20) {
            brakeMode()
        }
        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                Lift.heightEntry.setDouble(Lift.height)
            }
        }
    }

    fun setPower(power: Double) {
        Lift.liftMotor.setPercentOutput(power)
    }
}