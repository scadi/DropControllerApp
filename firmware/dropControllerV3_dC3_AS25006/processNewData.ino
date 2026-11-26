
/*
****************************************
* Function processNewData() 
* 
* Most commands are dealt with as they are received. 
* Drop data is copied to temporary variables and processed later. Done this way to avoid odd issues I had with serial data buffering.
* 
* 
* ASCII - 48 converts ASCII number in to actual value. For example "1" in to the value 1
* 
*/


void processNewData()  
{

 

    // NDx  Number of drops
    if ( receivedChars[0]=='N' )   { numDropsCheck = receivedChars[1] - 48;  receivedChars[0] = '\0';      }


    else if ( receivedChars[0]=='S' )     { receivedChars[0] = '\0';   notConnected = false;   }


    // I use temporary variables to avoid buffer issues that sometimes happen when parsing the commands while still receiving data
    
    // copy the drop data to dropCommand[]
    else if (receivedChars[0]=='D') { numDrops++; strcpy(dropCommand[numDrops], receivedChars); receivedChars[0] = '\0'; }    

	  // copy the flash trigger commands
    else if (receivedChars[0] == 'F' ) 
    {    
           if       ( receivedChars[1] =='1')  { strcpy(flashCommand1, receivedChars);  haveFT1 = true;  }
           else if  ( receivedChars[1] =='2')  { strcpy(flashCommand2, receivedChars);  haveFT2 = true;  }
           else if  ( receivedChars[1] =='3')  { strcpy(flashCommand3, receivedChars);  haveFT3 = true;  }
           receivedChars[0] = '\0';
    }


		  // copy the camera trigger command to cameraCommand[]
    else if (receivedChars[0] == 'C' ) { strcpy(cameraCommand, receivedChars); haveCT = true; receivedChars[0] = '\0';   }


      // EOD - End Of Data packet   
    else if ( (receivedChars[0]=='E') && (receivedChars[1]=='O') && (receivedChars[2]=='D') ) { haveNewDrop = true; receivedChars[0] = '\0'; }

      


          

    // HELLO message
    else if (strcmp(receivedChars, "HELLO")  == 0)
    {
         // send a HELLO message back to the controller app
         Serial.print("[HELLO]");
         BTserial.print("[HELLO]");

        // turn off the flashing LED to show we are connected
        digitalWrite(LED_WAITING_PIN, HIGH); 
        notConnected = false;
        receivedChars[0] = '\0';
    }




    // Drain Valve
    // O11 - Valve 1 open    O10 - Valve 1 close
    else if ( receivedChars[0] == 'O'   )
    {  
       if (receivedChars[1]=='X') // all valves off
       {
          closeSolenoid(1);
          closeSolenoid(2);
          closeSolenoid(3);
          closeSolenoid(4);
          closeSolenoid(5);
          closeSolenoid(6);
       }

       else  // not X
       {
           tempValve = receivedChars[1] - 48;  
           tempOnOffFlag =  receivedChars[2] - 48;

           if (tempValve >0 && tempValve < 7)
           {
             if (tempOnOffFlag == 1) { openSolenoid(tempValve);  }  // open valve
             if (tempOnOffFlag == 0) { closeSolenoid(tempValve); }  // close valve
           }
       }
	   
       receivedChars[0] = '\0';
    }  





    // Firmware version
    else if (strcmp(receivedChars, "VERSION") == 0)
    {
       Serial.print("[V,");   Serial.print(versionNumber);    Serial.print(",");    Serial.print(compileDate);    Serial.print("]");
       BTserial.print("[V,"); BTserial.print(versionNumber);  BTserial.print(",");  BTserial.print(compileDate);  BTserial.print("]");
       receivedChars[0] = '\0';
    }      
	
	

   // Are You There. Connection test
    else if (strcmp(receivedChars, "AYT")  == 0) { Serial.print("[YIA]"); BTserial.print("[YIA]"); receivedChars[0] = '\0'; }       


    else if (strcmp(receivedChars, "INFO")  == 0) 
	{ 
		Serial.println(F("dropControllerV3")); 
		Serial.println(versionNumber);
		Serial.println(fileName);
		Serial.println(compileDate);
		Serial.println(" ");  
		
		receivedChars[0] = '\0';
	}  
	
	
	
    // BYE message
    // turn on the flashing LED to show we are no longer connected
    // var waiting is the flag used in flashing the LED
    else if (strcmp(receivedChars, "BYE")  == 0) { digitalWrite(LED_WAITING_PIN, LOW); notConnected = true; receivedChars[0] = '\0'; }       





     else 
	 {
		 // error. Data not recognised
	 }
	 
	 
	 

    haveNewData = false;  


} // void copyNewSerialData() 
