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
//    Intake.resetPivotOffset()
    Feeder.autoFeedMode = true
    Intake.setIntakePower(Intake.INTAKE_POWER)
    Intake.intakeState = Intake.Mode.INTAKE
    Intake.changeAngle(Intake.PIVOT_INTAKE)
    Intake.changeAngle(Intake.PIVOT_INTAKE)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
}

suspend fun catch() = use(Intake) {
//    Intake.resetPivotOffset()
    Feeder.autoFeedMode = true
    Intake.setIntakePower(0.0)
    Intake.intakeState = Intake.Mode.CATCH
    Intake.changeAngle(Intake.PIVOT_CATCH)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
}

suspend fun armUp() = use(Intake) {
//    Intake.resetPivotOffset()
    Feeder.autoFeedMode = false
    Intake.setIntakePower(0.0)
    Intake.intakeState = Intake.Mode.STOW
    Intake.changeAngle(Intake.PIVOT_STORE)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
}

suspend fun powerSave() = use(Intake) {
    if (!Feeder.isAuto) Feeder.autoFeedMode = false
    Intake.setIntakePower(0.0)
    Intake.intakeState = Intake.Mode.POWERSAVE
    Intake.resetPivotOffset()
    Intake.changeAngle(Intake.PIVOT_BOTTOM)
    Climb.climbMode = false
    Climb.climbIsPrepped = false
    Intake.resetPivotOffset()
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

suspend fun autoShootv2(shotCount: Int = 2, maxWait: Double = 2.5) = use(Shooter, Feeder, Drive) {
    var doneShooting = false
    Feeder.autoFeedMode = true
    Shooter.rpmSetpoint = 3000.0
    Shooter.shootMode = true
    val t = Timer()
    t.start()
    parallel({
        println("autoshooting   usingFrontLL ${Limelight.useFrontLimelight} distance ${Limelight.distance}")
        suspendUntil { Shooter.allGood || doneShooting }  // Limelight.aimError.absoluteValue < Shooter.aimMaxError && Shooter.rpmError.absoluteValue < Shooter.rpmMaxError || doneShooting }
        suspendUntil { doneShooting }
        Shooter.shootMode = false
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
//        suspendUntil { Feeder.autoCargoShot > 0 || doneShooting}
//        var startWait = t.get()
//        Feeder.waitASecond = true
//        suspendUntil { t.get() - startWait > 0.5 }
//        Feeder.waitASecond = false
        suspendUntil { Feeder.autoCargoShot >= shotCount || doneShooting }
        delay(0.1)
        if (!doneShooting) {
            println("doneShooting after ${Feeder.autoCargoShot} cargo in ${t.get()} seconds")
        }
        doneShooting = true
    }, {
        periodic {
            if (!doneShooting && t.get() > maxWait) {
                println("failed shoot allGood: ${Shooter.allGood} rpmGood ${Shooter.rpmGood} pitchGood ${Shooter.pitchGood} aimGood ${Shooter.aimGood} ")
                doneShooting = true
                println("doneShooting after $maxWait sec")
            } else if (doneShooting) {
                stop()
            }
        }
    })
    Shooter.shootMode = false
}

suspend fun autoShoot() = use(Shooter, Feeder, Drive) {
    Feeder.setShooterFeedPower(0.0)
    Shooter.shootMode = true
    var doneShooting = false
    var t = Timer()
    t.start()
    parallel({
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

suspend fun goToPose(targetPose: Pose, fullCurve: Boolean = false, minTime: Double = 0.0) = use(Climb) {
    val time = if (fullCurve) {
        maxOf(minTime, Climb.angleChangeTime(targetPose.angle), Climb.heightChangeTime(targetPose.height))
    } else {
        minTime
    }
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

    parallel({
        Intake.changeAngle(Intake.PIVOT_BOTTOM)
    }, {
        Shooter.changeAngle(Shooter.PITCH_LOW)
    })
    goToPose(Pose.CLIMB_PREP)
    Climb.climbIsPrepped = true
    Climb.bungeeTakeOver = true
    println("climb is prepped")
    suspendUntil { OI.operatorRightTrigger > 0.1 || OI.operatorLeftTrigger > 0.1 }
    performClimb()
}


suspend fun performClimb() {
    println("trying to start climb")
    if (Climb.climbIsPrepped) {
        //println("Climb stage executing: ${Climb.climbStage} roll: ${Climb.roll}")
        OI.operatorController.rumble = 0.5
        var loop = 0
        var lasTroll = Climb.roll
        while (true) {
            Climb.climbStage = 0
            while (Climb.climbStage < 6) {
                if (OI.operatorRightTrigger > 0.1 || OI.operatorLeftTrigger > 0.1) {
                    if (loop == 0) Climb.bungeeTakeOver = false
                    println("Trigger climb stage ${Climb.climbStage}, loop $loop, roll is ${Climb.roll}")
                    when (Climb.climbStage) {
                        0 -> {
//                            Climb.angleMotor.brakeMode()
                            goToPose(Pose.PULL_UP)
                            if (loop > 0) delay(0.1)
                        }
                        1 -> {
                            goToPose(Pose.PULL_UP_LATCH, false, 0.5)
                            delay(0.1)
                            goToPose(Pose.PULL_UP_LATCH_LIFT, false, 0.45)
                            goToPose(Pose.PULL_UP_LATCH_RELEASE, true)
                            if (loop == 0) {
                                delay(0.1)
                            } else {
                                periodic {
                                    if (lasTroll - Climb.roll < 0.0 && Climb.roll > 10.0)
                                        stop()
                                }
                                lasTroll = Climb.roll
                                delay(0.1)
                            }
                        }
                        2 -> {
                            goToPose(Pose.EXTEND_HOOKS)
//                            if (loop == 0) {
//                                delay(0.5)
//                            } else {
                                val angleTimer = Timer()
                                var hit25 = false
                                angleTimer.start()
                                periodic {
                                    if (!hit25 && Climb.roll > 25.0) {
                                        hit25 = true
                                        println("hit 30, angle ${Climb.angle}")
                                    }
                                    val deltaRoll = Climb.roll - lasTroll
//                                    var maxRoll = if (loop == 0) 18.0 else 15.0
                                    if (deltaRoll > -3.0 && deltaRoll < 1.0 && Climb.angle > 25.0 && Climb.roll < 15.0) {
                                        println("Angle ${angleTimer.get()} Roll ${Climb.roll} DeltaRoll $deltaRoll")
                                        stop()
                                    }
                                    lasTroll = Climb.roll
                                }
//                            }
                        }
                        3 -> {
                            goToPose(Pose.TRAVERSE_ENGAGE)
//                            delay(0.2)
                            if (loop == 0) delay(0.2) else delay(0.1)
                        }
                        4 -> {
                            goToPose(Pose.TRAVERSE_PULL_MID, false, 0.5)
                            if (loop == 0) delay(0.24) else delay(0.04)
                        }
                        5 -> goToPose(Pose.TRAVERSE_PULL_UP, false, 0.5)

                        else -> println("Climb Stage Complete")
                    }
                }
                Climb.climbStage += 1
            }
            loop += 1
        }
    }
    OI.operatorController.rumble = 0.0
    println("done with start climb")
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
