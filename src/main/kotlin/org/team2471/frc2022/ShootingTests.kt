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
            feedbackCoefficient = 60.0 / (2048 * 0.49825)
            coastMode()
            pid {
                p(0.8e-5) //1.5e-8)
                i(0.0)//i(0.0)
                d(0.0)//d(1.5e-3) //1.5e-3  -- we tried 1.5e9 and 1.5e-9, no notable difference  // we printed values at the MotorController and the wrapper
                f(0.03696) //0.000045
            }
        }

        rpmSetpointEntry.setDouble(1600.0)

        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                rpmEntry.setDouble(rpm)
                rpmErrorEntry.setDouble(rpmSetpoint - rpm)
                if(OI.driverController.rightTrigger > 0.5) {
                    rpm = rpmSetpoint
                    println("Got into rpm tests $rpm $rpmSetpoint. Hi.")
//                    shootingMotor.setPercentOutput(OI.driveRightTrigger)

                } else {
                    rpm = 0.0
                }
            }
        }
    }

    var rpm: Double
        get() = shootingMotor.velocity
        set(value) {
            println("rpm set to $value")
            shootingMotor.setVelocitySetpoint(value, 1.0 / 6000)
        }
    var rpmSetpoint: Double = 0.0
        get() {
            return rpmSetpointEntry.getDouble(1600.0)
        }
}