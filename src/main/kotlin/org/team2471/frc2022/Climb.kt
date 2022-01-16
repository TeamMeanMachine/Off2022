package org.team2471.frc2022

import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.framework.Subsystem

object Climb : Subsystem("Climb") {
    val climbMotor = MotorController(FalconID(Falcons.CLIMB), FalconID(Falcons.CLIMB_TWO))
    val climbPivotMotor = MotorController(FalconID(Falcons.CLIMB_PIVOT))

    init {

    }

    fun setPower(power: Double) {
        climbMotor.setPercentOutput(power)
    }

    override suspend fun default() {

    }
}