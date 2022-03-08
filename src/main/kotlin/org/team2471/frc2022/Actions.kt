package org.team2471.frc2022

import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.parallel
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use
import kotlin.math.roundToInt

//Intake


suspend fun intake() = use(Intake, Climb) {
    Intake.setIntakePower(Intake.INTAKE_POWER)
    Intake.changeAngle(Intake.PIVOT_INTAKE)
}

suspend fun catch() = use(Intake, Climb) {
    Intake.setIntakePower(0.0)
    Intake.changeAngle(Intake.PIVOT_CATCH)
}

suspend fun armUp() = use(Intake, Climb) {
    Intake.setIntakePower(0.0)
    Intake.changeAngle(Intake.PIVOT_TOP)
}

suspend fun feedUntilCargo() = use(Intake, Feeder) {
    periodic {
        if (Shooter.cargoIsStaged) {
            Feeder.setShooterFeedPower(0.0)
            println("Shooter Staged")
            if (Feeder.ballIsStaged) {
                Intake.setIntakePower(0.0)
                println("Intake Staged")
            } else {
                Intake.setIntakePower(Intake.INTAKE_POWER)
                println("Intake Powering - waiting for 2nd cargo")
            }
        } else {
            Feeder.setShooterFeedPower(0.9)
            Intake.setIntakePower(Intake.INTAKE_POWER)
            println("Feeder Power")
        }
    }
}

suspend fun shootMode() = use(Shooter) {
    println("shoot mode has been called. Shootmode = ${Shooter.shootMode}")
    Shooter.shootMode = !Shooter.shootMode
    Limelight.backLedEnabled = (Shooter.shootMode && !Limelight.useFrontLimelight)
    Limelight.frontLedEnabled = (Shooter.shootMode && !Limelight.useFrontLimelight)
}

suspend fun autoShoot() = use(Shooter, Feeder, Drive) {
    Shooter.shootMode = true
    parallel ({
        Feeder.setBedFeedPower(Feeder.BED_FEED_POWER)
        println("autoshooting   usingFrontLL ${Limelight.useFrontLimelight} distance ${Limelight.distance}")
        Feeder.autoFeedMode = false
        delay(0.5)
        Feeder.setShooterFeedPower(0.8)
        delay(2.0)
        Feeder.setShooterFeedPower(0.0)
        Shooter.shootMode = false
        Feeder.autoFeedMode = true
    }, {
        periodic {
            Drive.autoSteer()
            println("rpm ${Shooter.rpm.roundToInt()}     rpmSetpoint ${Shooter.rpmSetpoint.roundToInt()}    pitch ${Shooter.pitch.roundToInt()}       pitchSetpoint ${Shooter.pitchSetpoint.roundToInt()}")
            if (!Shooter.shootMode) {
                stop()
            }
        }
    })
}

suspend fun intakePivotTest() = use(Intake) {
//    zeroIntakePivot()
    periodic {
        Intake.setIntakePivotPower(OI.driveLeftTrigger - OI.driveRightTrigger)
    }
}

fun zeroIntakePivot() {
//    try {
//        println("reinitializing pivot motor position")
//        periodic {
//            Intake.setIntakePivotPower(0.2)
//            println("${Intake.intakePivotMotor.current}")
//            if (Intake.intakePivotMotor.current > 60.0) {
//                stop()
//            }
//        }
//    }
//    finally {
//        println("finally has been reached")
        Intake.setIntakePivotPower(0.0)
//        Intake.pivotAngle = 95.0
//    }
}

suspend fun shootTest2() = use(Shooter, Feeder) {
    println("Got into shoot test")
    Feeder.setShooterFeedPower(0.9)
//    Intake.setIntakePower(0.8)
    periodic {
        Shooter.rpm = Shooter.rpmSetpoint
        println("in shooter test. Hi.")
    }
}

suspend fun goToPose(targetPose: Pose, fullCurve : Boolean = false, minTime: Double = 0.0) = use(Climb, Intake) {
    val time = if (fullCurve) {maxOf(minTime, Climb.angleChangeTime(targetPose.angle), Climb.heightChangeTime(targetPose.height))} else {minTime}
    println("Pose Values: $time ${targetPose.height} ${targetPose.angle}")
    parallel({
        Climb.changeHeight(targetPose.height, time)
    }, {
        Climb.changeAngle(targetPose.angle, time)
    })
}

suspend fun climbPrep() = use(Climb, Shooter, Intake) {
    Feeder.autoFeedMode = false
    Climb.climbMode = true
    Drive.limitingFactor = 0.25
    Climb.setStatusFrames(forClimb = true)
    Climb.changeAngle(8.0, 0.3)
    parallel ({
        Intake.changeAngle(Intake.PIVOT_BOTTOM)
    }, {
        Shooter.changeAngle(Shooter.PITCH_LOW)
    })
    goToPose(Pose.CLIMB_PREP)
    Climb.climbIsPrepped = true
    println("climb is prepped")
}

suspend fun  startClimb() = use(Climb, Intake) {
    println("trying to start climb")
    if (Climb.climbIsPrepped) {
        println("Climb stage executing: ${Climb.climbStage}")
        OI.operatorController.rumble = 0.5
        when (Climb.climbStage){
            0 -> {
                Climb.angleMotor.brakeMode()
                goToPose(Pose.PULL_UP)
            }
            1 -> {
                goToPose(Pose.PULL_UP_LATCH, true, 0.5)
                goToPose(Pose.PULL_UP_LATCH_RELEASE, true, 1.0)
            }
            2 -> goToPose(Pose.EXTEND_HOOKS)
            3 -> goToPose(Pose.TRAVERSE_ENGAGE)
            4 -> goToPose(Pose.TRAVERSE_PULL_UP)
            else -> Climb.climbStage = -1
        }
        Climb.climbStage += 1
        OI.operatorController.rumble = 0.0
    }
}