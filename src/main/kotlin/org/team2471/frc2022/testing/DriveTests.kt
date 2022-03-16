package org.team2471.frc2022.testing

import org.team2471.frc2022.Drive
import org.team2471.frc2022.OI
import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.math.Vector2
import org.team2471.frc.lib.math.round
import org.team2471.frc.lib.motion.following.drive
import org.team2471.frc.lib.motion.following.resetOdometry
import org.team2471.frc.lib.motion.following.tuneDrivePositionController
import org.team2471.frc.lib.units.degrees
import org.team2471.frc.lib.util.Timer

suspend fun Drive.steeringTests() = use(this) {
    println("Got into steeringTests. Hi")
    for (module in 0..3) {
        println("Got into first for. Hi.")
        for (quadrant in 0..4) {
            Drive.modules[module].angleSetpoint = (quadrant * 90.0).degrees
            delay(0.25)
        }
        delay(0.5)
    }
}
//

suspend fun Drive.driveTests() = use(this) {

    for (i in 0..3) {
            Drive.modules[i].setDrivePower(0.5)
            delay(1.0)
            Drive.modules[i].setDrivePower(0.0)
            delay(0.2)
    }
}

suspend fun Drive.fullTest() = use(this) {
    periodic {
        Drive.drive(OI.driveTranslation, OI.driveRotation)
    }
}

suspend fun Drive.tuneDrivePositionController() = use(this) {
    tuneDrivePositionController(OI.driverController)
}

suspend fun Drive.rampTest() = use(Drive) {
    Drive.resetOdometry()
    val t = Timer()
    t.start()
    periodic {
        drive(
            Vector2(0.0, 1.0),
            0.0,
            false
        )
        if (t.get() > 2.0) {
            drive(
                Vector2(0.0, 0.0),
                0.0,
                false
            )
            stop()
        }
    }
    println("endPos: ${Drive.position.y}")
}
