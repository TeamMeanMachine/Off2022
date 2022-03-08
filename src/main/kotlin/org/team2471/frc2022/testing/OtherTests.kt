package org.team2471.frc2022.testing

import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.input.Controller
import org.team2471.frc2022.*

suspend fun Feeder.motorTest() = use(this) {
    periodic {
//        setShooterFeedPower(0.4)
        setBedFeedPower(0.8)
    }
}
suspend fun Shooter.pitchTest() = use(this) {
    periodic {
        println("${(org.team2471.frc2022.OI.operatorRightTrigger - org.team2471.frc2022.OI.operatorLeftTrigger) * 0.5}")
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

suspend fun Climb.anglePIDTest() = use(this){
    var setpoint = 0.0
    periodic {
        setpoint = (OI.operatorController.leftThumbstickY * 10.0 ) + 30.0
        angleSetpoint = setpoint
        updatePositions()
        println("climb setpoint: $setpoint")
    }
}

suspend fun Intake.pivotTest() = use(this) {
    periodic {
        intakePivotMotor.setPercentOutput((OI.operatorRightTrigger - OI.operatorLeftTrigger) * 0.5)
        println("pivotTest active")
    }
}

suspend fun Climb.adjustmentTest() = use(this) {
    periodic {
        heightSetpoint -= OI.operatorController.leftThumbstickY * (12.0 / 50.0)
        angleSetpoint -= OI.operatorController.rightThumbstickX * (20.0 / 50.0)
    }
}

suspend fun Drive.autoSteerTest() = use(Drive){
    Limelight.backLedEnabled = true
    periodic {
        autoSteer()
    }
}

suspend fun Climb.feedForwardTest() = use(this) {
    var power = 0.0
    var upPressed = false
    var downPressed = false
    periodic {
        if (OI.driverController.dPad == Controller.Direction.UP) {
            upPressed = true
        } else if (OI.driverController.dPad == Controller.Direction.DOWN) {
            downPressed = true
        }
        if (OI.driverController.dPad != Controller.Direction.UP && upPressed) {
            upPressed = false
            power += 0.01
        }
        if (OI.driverController.dPad != Controller.Direction.DOWN && downPressed) {
            downPressed = false
            power -= 0.01
        }
        Climb.angleSetPower(power)
        println("power: $power")
    }
}

suspend fun Shooter.pitchClimbTest() {
    changeAngle(20.0)
    delay(4.0)
    changeAngle(31.0)
    delay(4.0)
    changeAngle(20.0)
    changeAngle(31.0)
    changeAngle(20.0)
    changeAngle(31.0)
    changeAngle(20.0)
    changeAngle(31.0)
    changeAngle(20.0)
    changeAngle(31.0)
    changeAngle(20.0)
    changeAngle(31.0)
}