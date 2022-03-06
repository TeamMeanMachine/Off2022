package org.team2471.frc2022

import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.parallel
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use
import org.team2471.frc2022.Pose.Companion.CLIMB_PREP

//Intake


suspend fun intake() = use(Intake) {
    Intake.setIntakePower(Intake.INTAKE_POWER)
    Intake.changeAngle(Intake.PIVOT_INTAKE)
    Climb.changeAngle(Climb.HOLDING_ANGLE)
}

suspend fun catch() = use(Intake) {
    Intake.setIntakePower(0.0)
    Intake.changeAngle(Intake.PIVOT_CATCH)
    Climb.changeAngle(Climb.HOLDING_ANGLE)
}

suspend fun armUp() = use(Intake) {
    Intake.setIntakePower(0.0)
    Intake.changeAngle(Intake.PIVOT_TOP)
    Climb.changeAngle(Climb.HOLDING_ANGLE)
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
    Limelight.ledEnabled = Shooter.shootMode
}

suspend fun autoShoot() = use(Shooter, Feeder) {
    println("autoshooting")
    Shooter.shootMode = true
    delay(0.5)
    Feeder.feed(10.0)
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

suspend fun goToPose(targetPose: Pose) = use(Climb, Intake) {
    parallel({
        Climb.changeHeight(targetPose.height)
    }, {
        Climb.changeAngle(targetPose.angle)
    }, {
        Intake.changeAngle(targetPose.intake)
    })
}

suspend fun climb() = use(Climb, Intake) {
    Climb.changeAngle(8.0) // takes brake off climb height
    goToPose(CLIMB_PREP)
}