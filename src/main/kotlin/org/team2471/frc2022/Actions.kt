package org.team2471.frc2022

import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.parallel
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.coroutines.suspendUntil
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.util.Timer
import kotlin.math.absoluteValue

//Intake


suspend fun intake() = use(Intake) {
    Intake.resetPivotOffset()
    Feeder.autoFeedMode = true
    Intake.setIntakePower(Intake.INTAKE_POWER)
    Intake.intakeState = Intake.Mode.INTAKE
    Intake.changeAngle(Intake.PIVOT_INTAKE)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
}

suspend fun catch() = use(Intake) {
    Intake.resetPivotOffset()
    Feeder.autoFeedMode = true
    Intake.setIntakePower(0.0)
    Intake.intakeState = Intake.Mode.CATCH
    Intake.changeAngle(Intake.PIVOT_CATCH)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
}

suspend fun armUp() = use(Intake) {
    Intake.resetPivotOffset()
    Feeder.autoFeedMode = false
    Intake.setIntakePower(0.0)
    Intake.intakeState = Intake.Mode.STOW
    Intake.changeAngle(Intake.PIVOT_TOP)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
}
suspend fun powerSave() = use(Intake) {
    Intake.resetPivotOffset()
    Feeder.autoFeedMode = false
    Intake.setIntakePower(0.0)
    Intake.intakeState = Intake.Mode.POWERSAVE
    Intake.resetPivotOffset()
    Intake.changeAngle(Intake.PIVOT_BOTTOM)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
}

suspend fun feedUntilCargo() = use(Intake, Feeder) {
    periodic {
        if (Shooter.cargoIsStaged) {
            Feeder.setShooterFeedPower(0.0)
            println("Shooter Staged")
            if (Feeder.cargoIsStaged) {
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
    Limelight.frontLedEnabled = (Shooter.shootMode && Limelight.useFrontLimelight)
}

suspend fun autoShootv2(shotCount : Int = 2, maxWait: Double = 2.5) = use(Shooter, Feeder, Drive) {
    var doneShooting = false
    Feeder.autoFeedMode = true
    Shooter.shootMode = true
    val t = Timer()
    t.start()
    parallel ({
        println("autoshooting   usingFrontLL ${Limelight.useFrontLimelight} distance ${Limelight.distance}")
        suspendUntil { Limelight.aimError.absoluteValue < 2.0 && Shooter.rpmError.absoluteValue < 300.0 || doneShooting }
        suspendUntil { doneShooting }
        Shooter.shootMode = false
        Feeder.autoFeedMode = true
    }, {
        periodic {
            Drive.autoSteer()
//            println("rpm ${Shooter.rpm.roundToInt()}     rpmSetpoint ${Shooter.rpmSetpoint.roundToInt()}    pitch ${Shooter.pitch.roundToInt()}       pitchSetpoint ${Shooter.pitchSetpoint.roundToInt()}")
            if (doneShooting) {
                stop()
            }
        }
        println("aimError = ${Limelight.aimError}")
    }, {
        Feeder.autoCargoShot = 0
        suspendUntil {Feeder.autoCargoShot >= shotCount || doneShooting}
        delay(0.1)
        if (!doneShooting) {
            println("doneShooting after 2 cargo")
        }
        doneShooting = true
    }, {
        periodic {
            if (!doneShooting && t.get() > maxWait) {
                doneShooting = true
                println("doneShooting after $maxWait sec")
            } else if (doneShooting) {
                stop()
            }
        }
    })
    Shooter.shootMode = false
    Feeder.autoFeedMode = false
}

suspend fun autoShoot() = use(Shooter, Feeder, Drive) {
    Feeder.setShooterFeedPower(0.0)
    Shooter.shootMode = true
    var doneShooting = false
    var t = Timer()
    t.start()
    parallel ({
        println("autoshooting   usingFrontLL ${Limelight.useFrontLimelight} distance ${Limelight.distance}")
        Feeder.autoFeedMode = true
        Feeder.setBedFeedPower(Feeder.BED_FEED_POWER)
        delay(1.0)
        suspendUntil { Limelight.aimError.absoluteValue < 4.0 || doneShooting }
        println("aimError: ${Limelight.aimError}      doneShooting? $doneShooting")
        Feeder.setShooterFeedPower(0.8)
        suspendUntil { doneShooting }
        Feeder.setShooterFeedPower(0.0)
        Shooter.shootMode = false
        Feeder.autoFeedMode = true
    }, {
        periodic {
            Drive.autoSteer()
//            println("rpm ${Shooter.rpm.roundToInt()}     rpmSetpoint ${Shooter.rpmSetpoint.roundToInt()}    pitch ${Shooter.pitch.roundToInt()}       pitchSetpoint ${Shooter.pitchSetpoint.roundToInt()}")
            if (doneShooting) {
                stop()
            }
        }
        println("aimError = ${Limelight.aimError}")
    }, {
        suspendUntil { Shooter.cargoIsStaged || doneShooting }
        suspendUntil { !Shooter.cargoIsStaged || doneShooting }
        suspendUntil { Shooter.cargoIsStaged || doneShooting }
        suspendUntil { !Shooter.cargoIsStaged || doneShooting }
        delay(0.1)
        if (!doneShooting) {
            println("doneShooting after 2 cargo")
        }
        doneShooting = true
    }, {
        periodic {
            if (!doneShooting && t.get() > 2.5) {
                doneShooting = true
                println("doneShooting after 2.5 sec")
            } else if (doneShooting) {
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
        println("Climb stage executing: ${Climb.climbStage} roll: ${Climb.roll}")
        OI.operatorController.rumble = 0.5
        Climb.climbStage = 0
        while (Climb.climbStage < 5) {
            when (Climb.climbStage) {
                0 -> {
                    Climb.angleMotor.brakeMode()
                    goToPose(Pose.PULL_UP)
                }
                1 -> {
                    goToPose(Pose.PULL_UP_LATCH, true, 0.5)
                    goToPose(Pose.PULL_UP_LATCH_LIFT, true)
                    goToPose(Pose.PULL_UP_LATCH_RELEASE, true, 1.0)
                }
                2 -> goToPose(Pose.EXTEND_HOOKS)
                3 -> goToPose(Pose.TRAVERSE_ENGAGE)
                4 -> goToPose(Pose.TRAVERSE_PULL_UP)
                //else -> Climb.climbStage = -1
            }
            delay(0.5)
            Climb.climbStage += 1
        }
        OI.operatorController.rumble = 0.0
        println("done with start climb")
    }
}

//suspend fun midClimb()

suspend fun clearFeeder() = use(Feeder) {
    println("clearing out feeder and Intake")
    //val currFeedMode = Feeder.autoFeedMode
   // Feeder.autoFeedMode = false
    Feeder.isClearing = true
//    Feeder.setBedFeedPower(-Feeder.BED_FEED_POWER)
//    Feeder.setShooterFeedPower(-Feeder.SHOOTER_FEED_POWER)
    delay(0.5)
    //Feeder.autoFeedMode = currFeedMode
    Feeder.isClearing = false
}