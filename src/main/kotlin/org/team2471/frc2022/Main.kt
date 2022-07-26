@file:JvmName("Main")

package org.team2471.frc2022

import Off____.BuildConfig
import edu.wpi.first.wpilibj.RobotBase
import kotlinx.coroutines.DelicateCoroutinesApi
import org.team2471.frc.lib.framework.MeanlibRobot
import org.team2471.frc.lib.units.degrees
import org.team2471.frc2022.testing.*
import java.net.NetworkInterface

var isCompBot = true

@DelicateCoroutinesApi
object Robot : MeanlibRobot() {

    var startMeasureTime = System.nanoTime()
    var lastMeasureTime = startMeasureTime
    init {
        val networkInterfaces =  NetworkInterface.getNetworkInterfaces()
        for (iFace in networkInterfaces) {
            if (iFace.name == "eth0") {
                   println("NETWORK NAME--->${iFace.name}<----")
                   var macString = ""
                   for (byteVal in iFace.hardwareAddress){
                    macString += String.format("%s", byteVal)
                }
                println("FORMATTED---->$macString<-----")

                isCompBot = (macString != "0-12847512372")
                println("I am compbot = $isCompBot")
            }
        }

        // i heard the first string + double concatenations were expensive...
        repeat(25) {
            println("RANDOM NUMBER: ${Math.random()}")
        }
        println("TAKE ME HOOOOOME COUNTRY ROOOOOOOOADS TOOO THE PLAAAAAAACE WHERE I BELOOOOOOOOONG")
        println(BuildConfig.BUILD_TIME)
        Drive.zeroGyro()
        Drive.heading = 0.0.degrees
        AutoChooser
        PowerInfo
    }

    override suspend fun enable() {
        println("starting enable")
        Drive.enable()
        //FrontLimelight.enable()
//        Drive.initializeSteeringMotors()
//        zeroIntakePivot()
        println("ending enable")
        PowerInfo.enable()
    }

    override suspend fun autonomous() {
       initTimeMeasurement()
        println("autonomous starting")
//        Drive.zeroGyro()
        Drive.brakeMode()
        Drive.aimPDController = Drive.autoPDController
        println("autonomous Drive brakeMode ${totalTimeTaken()}")
        AutoChooser.autonomous()
        println("autonomous ending ${totalTimeTaken()}")
    }

    override suspend fun teleop() {
        println("telop begin")
        Drive.aimPDController = Drive.teleopPDController
        Drive.headingSetpoint = Drive.heading
    }

    override suspend fun test()  {
        println("test mode begin. Hi.")
        Drive.driveCircle()
    }


    override suspend fun disable() {
        Drive.disable()
        PowerInfo.disable()
        OI.operatorController.rumble = 0.0
    }
    private fun initTimeMeasurement(){
        startMeasureTime = System.nanoTime()
        lastMeasureTime = startMeasureTime
    }
    private fun updateNanosTaken(){
        lastMeasureTime = System.nanoTime()
    }
    fun totalTimeTaken(): Long {
        return System.nanoTime() - startMeasureTime
    }
    fun recentTimeTaken(): Long {
        val timeTaken = System.nanoTime() - lastMeasureTime
        updateNanosTaken()
        return timeTaken
    }
}

fun main() {
    println("start robot")
    RobotBase.startRobot { Robot }
}