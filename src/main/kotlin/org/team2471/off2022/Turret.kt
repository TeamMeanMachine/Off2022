package org.team2471.off2022


import edu.wpi.first.networktables.NetworkTableInstance
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.units.Angle
import org.team2471.frc.lib.units.degrees

object Turret : Subsystem("Turret") {
    val turretMotor = MotorController(FalconID(Falcons.TURRET))

    private val table = NetworkTableInstance.getDefault().getTable(Turret.name)
    val turretAngleEntry = table.getEntry("Turret Angle")

    const val DEFAULT_SPEED = 0.4
    var angle : Double = 0.0
        get() = turretMotor.angle

    var angleSetpoint : Double
        get() = turretAngleEntry.getDouble(0.0)
        set(value) {
//            field = value.coerceIn(PIVOT_BOTTOM, PIVOT_TOP) + pivotDriverOffset
            turretAngleEntry.setDouble(value)
        }

    init {
        turretMotor.config(20) {
            brakeMode()
        }
        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                turretAngleEntry.setDouble(angle)
            }
        }
    }

    fun setPower(power: Double) {
        turretMotor.setPercentOutput(power)
    }
}