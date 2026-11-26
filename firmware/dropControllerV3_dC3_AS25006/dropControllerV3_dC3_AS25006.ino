/*
*      _                      _____               _                _  _              
*     | |                    / ____|             | |              | || |            
*   __| | _ __  ___   _ __  | |      ___   _ __  | |_  _ __  ___  | || |  ___  _ __ 
*  / _` || '__|/ _ \ | '_ \ | |     / _ \ | '_ \ | __|| '__|/ _ \ | || | / _ \| '__| 
* | (_| || |  | (_) || |_) || |____| (_) || | | || |_ | |  | (_) || || ||  __/| |   
*  \__,_||_|   \___/ | .__/  \_____|\___/ |_| |_| \__||_|   \___/ |_||_| \___||_|      
*                    | |                                                                                                      
*                    |_|                                                                            
*
* By Martyn Currey.  
* www.dropController.com
* Copyright 2025. All rights reserved.
* Requires 
* - Android app version 30401 or higher
* - Windows app version 25.0.0.0 or higher
* 
* dropController is a device for controlling solenoid valves for creating water drop collisions
* 
* 1 x camera connection (shutter & focus)
* 3 x flash/Aux trigger connections
* 6 x solenoid valve connections
* 
* Receives commands from an Android app (via Bluetooth) or a Windows program (vis USB)
* dropControllerV3 is DIY only. See www.dropcontroller.com for details.
*
*/

// Version
const char versionNumber[] = "dC3_AS25006";
const char fileName[] = __FILE__;
const char compileDate[] = __DATE__;

boolean USB_DEBUG = false;
// when true, output debug information to the serial monitor via the usb connection
// For this to work the dropController has to be used with the Android app using Bluetooth

boolean SOFTSERIAL_DEBUG = false;
// output debug information through the software serial connection. Best used with a UART to USB adapter, Baud rate is 9600
// SOFTSERIAL_DEBUG requires the dropController to be used with the Windows App



/*
*    _____                                                _      
*   / ____|                                              | |     
*  | |      ___   _ __ ___   _ __ ___    __ _  _ __    __| | ___ 
*  | |     / _ \ | '_ ` _ \ | '_ ` _ \  / _` || '_ \  / _` |/ __|
*  | |____| (_) || | | | | || | | | | || (_| || | | || (_| |\__ \
*   \_____|\___/ |_| |_| |_||_| |_| |_| \__,_||_| |_| \__,_||___/
* 
* expects the following data via serial @ 9600
* commands must be enclosed in square brackets []
* Sn                 - S = start of data marker. n = sequence number. Used for debugging.
* NDn                - n = number of drops. Used to check we have data for all drops.
* D011222233333       - D = Drop Data. Drop 1, Solenoid 1, start at 2222ms, stop time 33333
* D026333344444       - D = Drop Data. Drop 2, Solenoid 6, start at 3333ms, stop time 44444
* 
* F1Y100001100       - F1 = Flash Trigger 1. Y = ON (N = OFF) Trigger time 1000. stop time 01100
* F2Y054000590       - F2 = Flash Trigger 1. Y = ON (N = OFF) Trigger time 0540. stop time 00590
* F3N                - F3 = Flash Trigger 3. N=OFF. 
* 
* CN                 - CT off
* CY100001050        - CT   Y=ON  1000 - trigger start time.   0150 trigger stop time  
* CB000001500        - Bulb mode. 0000 - trigger start time.  01500 trigger stop tuime
* 
*                      Mirror lockeu is not used in this version
* MN                 - M = Mirror Lock Up. N= NO.  
* MY1000             - M = Mirror Lock Up. Y= YES. 1000 = time in ms to wait after the mirror lock up trigger
* EOD                - EOD = End of Drop Data
* 
* O11                - Valve 1 open
* O10                - Valve 1 close
* O21                - Valve 2 open
* O20                - Valve 2 close
* O31                - Valve 3 open
* O30                - Valve 3 close
* OX                 - All valves close
* 
* HELLO              - Connection request from the control app.
* BYE                - connection closed by the control app.
* VERSION            - request for firmware version. 
* AYT                - connection check - may not be used in all apps (Are You There).
* 
* 
* Commands sent to the app
* HELLO             - Initial hand shake. Reply to incoming HELLO
* M,title,message   - Message. Message is displayed in a pop up in the app. Not currently used.
* YIA               - reply to AYT. Are You There. Yes I Am.
* V,version,compile date   - Reply to VERSION
* FTS               - End of sequence marker (Finished The Sequence)
* 
* 
*   _____   _             
*  |  __ \ (_)            
*  | |__) | _  _ __   ___ 
*  |  ___/ | || '_ \ / __|
*  | |     | || | | |\__ \
*  |_|     |_||_| |_||___/
*
* 02 - SOLENOID 6
* 03 - SOLENOID 5
* 04 - SOLENOID 4
* 05 - SOLENOID 3
* 06 - SOLENOID 2
* 07 - SOLENOID 1
* 08 - SPARE TRIGGER
* 09 - FLASH TRIGGER 2
* 10 - FLASH TRIGGER 1
* 11 - FOCUS
* 12 - SHUTTER
* 13 - 
* 14 A0 - LED - waiting. Normally yellow
* 15 A1 - LED - Active.  Normally green
* 16 A2 
* 17 A3 
* 18 A4 - SOFTWARE SERIAL TX (Bluetooth module)
* 19 A5 - SOFTWARE SERIAL RX (Bluetooth module)
* 20 A6
* 21 A7  
*
*
*/






#include <SoftwareSerial.h> 
SoftwareSerial BTserial(18, 19); // RX, TX  







// Pins
// The pins for software serial are hard coded. See above.

const byte LED_ACTIVE_PIN        = 15;
const byte LED_WAITING_PIN       = 14;

const byte CT_SHUTTER_PIN        = 12;
const byte CT_FOCUS_PIN          = 11;

const byte FT1_PIN               = 10;
const byte FT2_PIN               = 9;
const byte FT3_PIN               = 8;

const byte ST1_PIN               = 7; 
const byte ST2_PIN               = 6; 
const byte ST3_PIN               = 5; 
const byte ST4_PIN               = 4; 
const byte ST5_PIN               = 3; 
const byte ST6_PIN               = 2; 


unsigned int inCount = 0;



// variables ***********************************************************************************

byte maxNumDrops = 24;


// Trigger duration
// These values are over-written by the data from the control app
unsigned int  camTriggerPulseDuration      = 10;        // How long the camera trigger signal is active
unsigned int  flashTriggerPulseDuration    = 10;        // How long the flash trigger is active. 


// Arrays to hold the drop times
// I don't use element [0]. This means drop 1 is at position [1] 
byte sol[30];                        // the solenoid valve to use for the drop; 1 to 6
unsigned int dropStartTime[30];      // start time in millisecond 
unsigned int dropStopTime[30];       // stop time in millisecond 

// Arduino Nano unsigned ints are 2 byte values with a value range of 0 to 65535. 
// This means the max drop size the Arduino can store is 65535 ms or ~65.5 seconds
// However, the max that can be entered in the app is 9999 ms or ~10 seconds.
// The start time and size are 4 chartacter ascii values. This means the actual size can be from 0000 to 9999ms, ~9.9 seconds

unsigned int FT1_Time_Start  = 0;
unsigned int FT1_Time_Stop   = 0;
unsigned int FT2_Time_Start  = 0;
unsigned int FT2_Time_Stop   = 0;
unsigned int FT3_Time_Start  = 0;
unsigned int FT3_Time_Stop   = 0;
unsigned int CT_Time_Start   = 0;
unsigned int CT_Time_Stop    = 0;
unsigned int CT_Pulse        = 0;

// boolean blubMode       = false;
unsigned long bulbPre  = 100;
unsigned long bulbPost = 100;

byte numDrops      = 0;
byte numDropsCheck = 0;


// Variables used for receiving serial data  ************************************************
const byte numChars = 30;
char receivedChars[numChars];
boolean haveNewData = false;
boolean haveNewDrop = false;

// variables to hold received commands        ************************************************
char flashCommand1[15];
char flashCommand2[15];
char flashCommand3[15];
char cameraCommand[15];

// I do not use position 0. This means the data for drop 1 is at array position 1
char dropCommand[26][15];

boolean FT1_On = false;
boolean FT2_On = false;
boolean FT3_On = false;
boolean CT_On  = false;


//  variables used when checking received data 
// Very simple check to see if we have received all fields
boolean haveFT1 = false;
boolean haveFT2 = false;
boolean haveFT3 = false;
boolean haveCT = false;
boolean haveML = false;
boolean haveAllDrops = false;


// temporary variables used in processNewdata
byte tempValve = 0;
byte tempOnOffFlag = 0;
unsigned long temp = 0;

// temporary variables used in makeDrops
long unsigned seqStartTime = 0;  
boolean CamTriggered   = false;
boolean camDone = false;
boolean flash1_Triggered = false;
boolean flash1_Done = false;
boolean flash2_Triggered = false;
boolean flash2_Done = false;
boolean flash3_Triggered = false;
boolean flash3_Done = false;
boolean allDropsDone = false;
byte    currentDrop = 0;
boolean dropped[26]; 
boolean solOpen[26];
boolean done    = false;


boolean notConnected            = true;
boolean wait_LED_State          = LOW;
unsigned long wait_timeNow      = 0;
unsigned long wait_timePrevious = 0;
unsigned long wait_delay        = 0;


void setup() 
{
    BTserial.begin(9600);
    Serial.begin(9600);
    while (!Serial) {;}  // required for Leonardo and Micro
    Serial.println("dropControllerV3 DIY");
    Serial.println(versionNumber);
    Serial.println(__DATE__);
    
    delay(500);

    if(USB_DEBUG) 
    {
      Serial.println("debug started");    
    }
    if(SOFTSERIAL_DEBUG) 
    {
      BTserial.println("debug started");    
    }


 
    // status LEDs
    pinMode(LED_WAITING_PIN,OUTPUT); digitalWrite(LED_WAITING_PIN, LOW); 
    pinMode(LED_ACTIVE_PIN,OUTPUT);  digitalWrite(LED_ACTIVE_PIN, LOW); 
  
    // define the trigger pins and set the pins to low
    pinMode(ST1_PIN, OUTPUT);    digitalWrite(ST1_PIN, LOW); 
    pinMode(ST2_PIN, OUTPUT);    digitalWrite(ST2_PIN, LOW); 
    pinMode(ST3_PIN, OUTPUT);    digitalWrite(ST3_PIN, LOW);
    pinMode(ST4_PIN, OUTPUT);    digitalWrite(ST4_PIN, LOW); 
    pinMode(ST5_PIN, OUTPUT);    digitalWrite(ST5_PIN, LOW); 
    pinMode(ST6_PIN, OUTPUT);    digitalWrite(ST6_PIN, LOW);    
    
    pinMode(CT_FOCUS_PIN,   OUTPUT);     digitalWrite(CT_FOCUS_PIN,   LOW); 
    pinMode(CT_SHUTTER_PIN, OUTPUT);     digitalWrite(CT_SHUTTER_PIN, LOW); 
    pinMode(FT1_PIN, OUTPUT);            digitalWrite(FT1_PIN, LOW);      
    pinMode(FT2_PIN, OUTPUT);            digitalWrite(FT2_PIN, LOW);    
    pinMode(FT3_PIN, OUTPUT);            digitalWrite(FT3_PIN, LOW);   

    initialise();

}




void loop() 
{
	
		recvWithStartEndMarkersUSB();
		recvWithStartEndMarkersBT(); 
		if (haveNewData) 
		{ 
			Serial.println(receivedChars);
			processNewData(); 
		}   
					


		if(haveNewDrop)                       
		{      
			parseDropData();                   // copy the temp received data variables to the drop data arrays.

			if ( dropDataIsOK() )              // check we have all the drop data. This is a basic check only.
			{         
			   digitalWrite(LED_WAITING_PIN, LOW);
			   digitalWrite(LED_ACTIVE_PIN, HIGH);
			   makeDrops(); 
			   sendFinishedFlag();
			}
			else { showError();  reset(); }

			digitalWrite(LED_WAITING_PIN, HIGH);
			digitalWrite(LED_ACTIVE_PIN,  LOW);
			initialise();
		}


     // notConnected just flashes the led
     // This version does not have a connected/!connected state. This means it will receive and process drop data even in the waitng state.     
     if (notConnected)
     {
            wait_timeNow = millis();
            if (wait_timeNow - wait_timePrevious > wait_delay)
            {
                 wait_timePrevious = wait_timeNow;
                 if (wait_LED_State == LOW)   {  wait_LED_State = HIGH;    digitalWrite(LED_WAITING_PIN, HIGH);  wait_delay=100; }
                 else                         {  wait_LED_State = LOW;     digitalWrite(LED_WAITING_PIN, LOW);   wait_delay=900; }
            }
     }

}





void initialise()
{  
      numDrops = 0;
      numDropsCheck = 0;
      haveNewDrop  = false; 
      for (int i = 1; i < maxNumDrops; i++)   {   dropCommand[i][0]=' ' ;  dropCommand[i][1]=0 ;      }
      haveFT1      = false;
      haveFT2      = false;
      haveFT3      = false;
      haveCT       = false;
      haveML       = false;
      haveAllDrops = false;
}


void sendFinishedFlag()
{   
    char rc;
    while (Serial.available() > 0)    {  rc = Serial.read();   }  // not really required but makes me feel better.
    Serial.print(F("[FTS]"));
    while (BTserial.available() > 0)  {  rc = BTserial.read(); }  // not really required but makes me feel better.
    BTserial.print(F("[FTS]"));       
}


// the control app waits for the finished flag. If this is not sent a timeout triggers in the app.
void reset()
{
     sendFinishedFlag(); 
}



// This is probably redundant.
boolean dropDataIsOK()
{
      // I should expand this to check each field;   FT, CT, ML
      // and check to see if any drop data is blank
      
      boolean dataOK = true;
//      for (int i = 1; i < numDrops+1; i++)   
//      {  
//           if ( dropCommand[i][0]!='D')   {  dataOK = false;   }
//      }

      if (numDrops != numDropsCheck) { dataOK = false;  }
      if (dataOK == false) {  Serial.print("[M,ERR: DD]");   }  
      
      if (!haveFT1) { dataOK = false;   Serial.print(F("[M,ERR: no FT1]")); }   
      if (!haveFT2) { dataOK = false;   Serial.print(F("[M,ERR: no FT2]")); }    
      if (!haveFT3) { dataOK = false;   Serial.print(F("[M,ERR: no FT3]")); }    
      if (!haveCT)  { dataOK = false;   Serial.print(F("[M,ERR: no CT]"));  }    
      return dataOK;
      
}



void showError()
{

    // may add the facility to show an error message in the app (Note to self: Use the popup message function from the BCP app).
    
    for (int i = 1; i < 10; i++) 
    {
        digitalWrite(LED_WAITING_PIN, HIGH);
        digitalWrite(LED_ACTIVE_PIN, LOW); 
        delay(100); 
        digitalWrite(LED_WAITING_PIN, LOW);
        digitalWrite(LED_ACTIVE_PIN, HIGH); 
        delay(100); 
    }
}
