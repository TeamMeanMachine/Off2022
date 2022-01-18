package org.team2471.frc2022

import org.team2471.frc.lib.framework.Subsystem
import org.photonvision.PhotonCamera

object VisionCargo : Subsystem("VisionCargo") {
    val camera = PhotonCamera("photonvision")
    init {

    }
}