package org.team2471.frc2022.testing

import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use
import org.team2471.frc2022.*

suspend fun Feeder.motorTest() = use(this) {
    periodic {
//        setShooterFeedPower(0.4)
        setBedFeedPower(0.8)
    }
}
suspend fun Shooter.pitchTest() = use(this) {
    periodic {
        pitchSetPower((OI.operatorRightTrigger - OI.operatorLeftTrigger) * 0.5)
    }
}

suspend fun Climb.pivotTest() = use(this) {
    periodic {
        angleSetPower(0.2)
    }
}

suspend fun Climb.motorTest() = use(this) {
    periodic {
        angleSetPower(OI.driveLeftTrigger - OI.driveRightTrigger)
        setPower((OI.operatorRightTrigger - OI.operatorLeftTrigger) * 0.5)
    }
}

suspend fun Intake.pivotTest() = use(this) {
    periodic {
        intakePivotMotor.setPercentOutput((OI.operatorRightTrigger - OI.operatorLeftTrigger) * 0.5)
        println("pivotTest active")
    }
}
