# Easy Door
Easy Door is a facial recognition home security system that automatically unlocks doors for known residents of a home. If a non-resident is detected, their facial recognition data will be run through an online database containing photos of known criminals from a local police database, setting off an alarm if a match is found. Finally, EasyDoor does a threat analysis of the environment, detecting any potential weapons the person is carrying, then notifying you via e-mail of the perceived threat level of the individual outside your home.

Easy Door was awarded Best IoT Hack Using a Qualcomm Device and Pitch Perfect by the Peterborough Innovation Cluster at Electric City Hacks 2017.

Easy Door can be found on Devpost [here.](https://devpost.com/software/easydoor)

In the making of Easy Door, we utilized a DragonBoard 410c running Android and connected to a sensor mezzanine Arduino Genuino Board with Grove Base Shield V1.3 connected to a grove buzzer, servo motor and LED. We sent data to the Arduino using the Arduino’s BLE library, which allows the circuit to advertise itself to nearby bluetooth devices to be controlled remotely. We also utilized external libraries OpenCV and UVC Camera in order to connect the usb camera to the Android app base. Created detection app and companion app with Android Studio along with AWS S3 for data storage of photos, AWS Rekognition for facial recognition and comparison and AWS SNS for sending email notifications to users.
