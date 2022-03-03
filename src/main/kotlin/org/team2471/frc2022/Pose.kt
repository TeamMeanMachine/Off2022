package org.team2471.frc2022

data class Pose(val height: Double, val angle: Double, val intake: Double) {

    companion object {
        val current: Pose
            get() = Pose(Climb.height, Climb.angle, Intake.pivotAngle)

        val CLIMB_PREP = Pose(Climb.HEIGHT_VERTICAL_TOP, 0.0, Intake.PIVOT_BOTTOM)
        val PULL_UP = Pose(Climb.HEIGHT_BOTTOM, 0.0, Intake.PIVOT_BOTTOM)
        val EXTEND_HOOKS = Pose(Climb.HEIGHT_VERTICAL_TOP, 5.0, Intake.PIVOT_BOTTOM)
    }
}
