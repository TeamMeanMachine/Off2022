package org.team2471.frc2022

import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use

//Intake


suspend fun intake(state: Boolean) = use(Intake) {
    println("Got into intake fun. Hi.")
    Intake.setExtend(state)
//    delay(0.2)
//    suspendUntil { OI.operatorController.rightBumper }
//    Outtake.setExtend(false)
}

suspend fun intakePivotTest() = use(Intake) {
    periodic {
        Intake.setIntakePivotPower(OI.driveLeftTrigger - OI.driveRightTrigger)
    }
}

suspend fun zeroIntakePivot() = use(Intake) {
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
        Intake.pivotAngle = 95.0
//    }
}

suspend fun shootTest2() = use(Shooter, Feeder, Intake) {
    println("Got into shoot test")
    Feeder.setPower(0.9)
    Intake.setIntakePower(0.8)
    periodic {
        Shooter.rpm = Shooter.rpmSetpoint
        println("in shooter test. Hi.")
    }
}

suspend fun shoot() = use(Shooter/*, Feeder*/) {

}

suspend fun spit() = use(Shooter) {

}