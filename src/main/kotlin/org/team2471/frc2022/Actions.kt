package org.team2471.frc2022

import org.team2471.frc.lib.coroutines.suspendUntil
import org.team2471.frc.lib.framework.use

//Intake

suspend fun intake() = use(Intake) {
    Intake.extendIntake(true)
    suspendUntil { OI.operatorController.a }
    Intake.extendIntake(false)
}

