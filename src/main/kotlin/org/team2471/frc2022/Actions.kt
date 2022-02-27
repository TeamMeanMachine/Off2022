package org.team2471.frc2022

import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.units.degrees

//Intake


suspend fun intake() = use(Intake, Feeder) {
    Intake.setIntakePower(Intake.INTAKE_POWER)
    Intake.changeAngle(Intake.PIVOT_INTAKE)
    feedUntilCargo()
}

suspend fun catch() = use(Intake, Feeder) {
    Intake.setIntakePower(0.0)
    Intake.changeAngle(Intake.PIVOT_CATCH)
    feedUntilCargo()
}

suspend fun armDown() = use(Intake, Feeder) {
    Intake.setIntakePower(0.0)
    Intake.changeAngle(Intake.PIVOT_BOTTOM)
    Feeder.setPower(0.0)
}

suspend fun armUp() = use(Intake, Feeder) {
    Intake.setIntakePower(0.0)
    Intake.changeAngle(Intake.PIVOT_TOP)
    Feeder.setPower(0.0)
}

suspend fun feedUntilCargo() = use(Intake, Feeder) {
    periodic {
        if (Shooter.cargoIsStaged) {
            Feeder.setPower(0.0)
            println("Shooter Staged")
            if (Feeder.ballIsStaged) {
                Intake.setIntakePower(0.0)
                println("Intake Staged")
            } else {
                Intake.setIntakePower(Intake.INTAKE_POWER)
                println("Intake Powering - waiting for 2nd cargo")
            }
        } else {
            Feeder.setPower(0.9)
            Intake.setIntakePower(Intake.INTAKE_POWER)
            println("Feeder Power")
        }
    }
}

suspend fun shootMode() = use(Shooter, Feeder) {
    Shooter.shootMode = !Shooter.shootMode
    if (Shooter.shootMode) {
        FrontLimelight.ledEnabled = true
        periodic {
            Shooter.rpm = Shooter.rpmSetpoint
            Shooter.pitch = Shooter.pitchSetpoint
//            Shooter.changeAngle(Shooter.pitchSetpoint)
            Feeder.setPower(OI.driveRightTrigger)
        }
    } else {
        FrontLimelight.ledEnabled = false
        Shooter.rpm = 0.0
        Feeder.setPower(0.0)
    }
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
    Feeder.setPower(0.9)
//    Intake.setIntakePower(0.8)
    periodic {
        Shooter.rpm = Shooter.rpmSetpoint
        println("in shooter test. Hi.")
    }
}

suspend fun shoot() = use(Shooter/*, Feeder*/) {

}

suspend fun spit() = use(Shooter) {

}