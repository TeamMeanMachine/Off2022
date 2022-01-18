package org.team2471.frc2022
import org.team2471.frc2022.Falcons
import org.team2471.frc2022.Talons
import edu.wpi.first.networktables.NetworkTableInstance
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.actuators.SparkMaxID
import org.team2471.frc.lib.actuators.TalonID
import org.team2471.frc.lib.control.PDController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.input.Controller
import org.team2471.frc.lib.motion_profiling.MotionCurve
import org.team2471.frc.lib.units.Length
import org.team2471.frc.lib.units.asFeet
import kotlin.math.absoluteValue

object ShootingTests : Subsystem("ShootingTests") {
    private val shootingMotor = MotorController(FalconID(Falcons.SHOOTER), FalconID(Falcons.SHOOTER_TWO))
    private val table = NetworkTableInstance.getDefault().getTable(name)
    val rpmEntry = table.getEntry("RPM")
    val rpmSetpointEntry = table.getEntry("RPM Setpoint")
    val rpmErrorEntry = table.getEntry("RPM Error")
    val rpmOffsetEntry = table.getEntry("RPM Offset")


    init {
        shootingMotor.config {
            coastMode()
        }

        rpmSetpointEntry.setDouble(7000.0)

        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)
                if(OI.driverController.rightTrigger > 0.5) {
                    rpm = rpmSetpoint
                }else {
                    rpm = 0.0
                }
            }
        }
    }

    var rpm: Double
        get() = shootingMotor.velocity
        set(value) = shootingMotor.setVelocitySetpoint(value)
    var rpmSetpoint: Double = 0.0
        get() {
            return rpmSetpointEntry.getDouble(1600.0)
        }
}