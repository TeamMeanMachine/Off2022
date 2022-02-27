package org.team2471.frc2022.testing

import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use
import org.team2471.frc2022.*

suspend fun Feeder.motorTest() = use(this) {
    periodic {
        setPower(0.2)
    }
}
suspend fun Shooter.pitchTest() = use(this) {
    periodic {
        pitchSetPower((OI.operatorRightTrigger - OI.operatorLeftTrigger) * 0.5)
    }
}

suspend fun Shooter.motorTest() = use(this) {
    periodic {
        shootingMotor.setPercentOutput(0.2)
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

suspend fun Intake.motorTest() {
    periodic {
        setIntakePower(0.1)
    }
}
