@file:JvmName("Main")

package org.team2471.frc2022

import FRC____.BuildConfig
import edu.wpi.first.wpilibj.RobotBase
import kotlinx.coroutines.DelicateCoroutinesApi
import org.team2471.frc.lib.framework.MeanlibRobot
import org.team2471.frc.lib.units.degrees
import org.team2471.frc2022.testing.*
import java.net.NetworkInterface

var isCompBotIHateEverything = true

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

                isCompBotIHateEverything = true //(macString != "0-128472587-69")
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
       // AutoChooser
        FrontLimelight.startUp()
        FrontLimelight.ledEnabled = true
//        ShootingTests
        Intake
        Shooter
        Feeder
        //Climb
    }

    override suspend fun enable() {
        println("starting enable")
        Drive.enable()
        Intake.enable()
        Feeder.enable()
        //FrontLimelight.enable()
//        Drive.initializeSteeringMotors()
//        ShootingTests.enable()
        Shooter.enable()
        Climb.enable()
//        zeroIntakePivot()
        println("ending enable")
    }

    override suspend fun autonomous() {
       initTimeMeasurement()
        println("autonomous starting")
//        Drive.zeroGyro()
        Drive.brakeMode()
        println("autonomous Drive brakeMode ${totalTimeTaken()}")
        //AutoChooser.autonomous()
        println("autonomous ending ${totalTimeTaken()}")
    }

    override suspend fun teleop() {
        println("telop begin")
        Drive.headingSetpoint = Drive.heading
    }

    override suspend fun test()  {
        println("test mode begin. Hi.")
//        Climb.anglePIDTest()
        Climb.adjustmentTest()
//        Climb.motorTest()
    }


    override suspend fun disable() {
        Drive.disable()
        Intake.disable()
        Shooter.disable()
        Feeder.disable()
        Climb.disable()
        //FrontLimelight.disable()

        //FrontLimelight.ledEnabled = false

        //FrontLimelight.parallaxThresholdEntry.setPersistent()

//        val table = NetworkTableInstance.getDefault().getTable(Drive.name)
//        val angle1Entry = table.getEntry("Angle 1")
//        val angle2Entry = table.getEntry("Angle 2")
//        val angle3Entry = table.getEntry("Angle 3")
//        val angle4Entry = table.getEntry("Angle 4")
//
//        val module0 = (Drive.modules[0] as Drive.Module)
//        val module1 = (Drive.modules[1] as Drive.Module)
//        val module2 = (Drive.modules[2] as Drive.Module)
//        val module3 = (Drive.modules[3] as Drive.Module)
//
//        periodic {
////            Drive.recordOdometry()
//
//            //println(module0.analogAngle)
//            angle1Entry.setValue(module0.analogAngle.asDegrees)
//            angle2Entry.setValue(module1.analogAngle.asDegrees)
//            angle3Entry.setValue(module2.analogAngle.asDegrees)
//            angle4Entry.setValue(module3.analogAngle.asDegrees)
//        }
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