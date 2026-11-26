

/*
****************************************
* Function openSolenoid
* opens a solenoid valve
* done this way so that a constant value is used.
*
*/
void openSolenoid(byte s)
{
    // done this way so I can use a constant for the sol pin
    if      (s==1) { digitalWrite(ST1_PIN, HIGH);    }
    else if (s==2) { digitalWrite(ST2_PIN, HIGH);    }
    else if (s==3) { digitalWrite(ST3_PIN, HIGH);    }
    else if (s==4) { digitalWrite(ST4_PIN, HIGH);    }    
    else if (s==5) { digitalWrite(ST5_PIN, HIGH);    }   
    else if (s==6) { digitalWrite(ST6_PIN, HIGH);    }    
    
}


/*
****************************************
* Function closeSolenoid
* closes a solenoid valve
* 
*/
void closeSolenoid(byte s)
{
     // done this way so I can use a constant for the sol pin
    if      (s==1) { digitalWrite(ST1_PIN, LOW);    }
    else if (s==2) { digitalWrite(ST2_PIN, LOW);    }
    else if (s==3) { digitalWrite(ST3_PIN, LOW);    }
    else if (s==4) { digitalWrite(ST4_PIN, LOW);    }    
    else if (s==5) { digitalWrite(ST5_PIN, LOW);    }   
    else if (s==6) { digitalWrite(ST6_PIN, LOW);    }   
}




/*
****************************************
* Function recvWithStartEndMarkers
* reads serial data and returns the content between a start marker and an end marker.
* 
*  Lazy solution. I created 2 similar functions. One to handle hardware serial and one to handle software serial
* 
* Original recvWithStartEndMarkers by Robin2 of the Arduino forums
* See  http://forum.arduino.cc/index.php?topic=288234.0
* 
* Full Arduino serial guide at https://www.martyncurrey.com/arduino-serial-part-1/
*/

void recvWithStartEndMarkersBT() 
{
     static boolean recvInProgressBT = false;
     static byte ndx = 0;
     char startMarker = '[';
     char endMarker = ']';
     char rc;
     
     if (BTserial.available() > 0) 
     { 
          rc = BTserial.read();
          if (USB_DEBUG) { Serial.print(rc); }
          
          if (recvInProgressBT == true) 
          {
               if (rc != endMarker) 
               {
                    receivedChars[ndx] = rc;
                    ndx++;
                    if (ndx >= numChars) { ndx = numChars - 1; }
               }
               else 
               {
                     receivedChars[ndx] = '\0'; // terminate the string
                     recvInProgressBT = false;
                     ndx = 0;
                     haveNewData = true;
               }
          }
          else if (rc == startMarker) { recvInProgressBT = true; }
     }
}



/*
****************************************
* Function recvWithStartEndMarkersUSB
* reads serial data and returns the content between a start marker and an end marker.
* 
* Original recvWithStartEndMarkers by Robin2 of the Arduino forums
* See  http://forum.arduino.cc/index.php?topic=288234.0
* 
* Full Arduino serial guide at https://www.martyncurrey.com/arduino-serial-part-1/
*/
void recvWithStartEndMarkersUSB() 
{
     static boolean recvInProgress = false;
     static byte ndx = 0;
     char startMarker = '[';
     char endMarker = ']';
     char rc;

     if (Serial.available() > 0) 
     {
          rc = Serial.read();
          if (USB_DEBUG) { Serial.print(rc); }
          
          if (recvInProgress == true) 
          {
               if (rc != endMarker) 
               {
                    receivedChars[ndx] = rc;
                    ndx++;
                    if (ndx >= numChars) { ndx = numChars - 1; }
               }
               else 
               {
                     receivedChars[ndx] = '\0'; // terminate the string
                     recvInProgress = false;
                     ndx = 0;
                     haveNewData = true;
               }
          }
          else if (rc == startMarker) { recvInProgress = true; }
     }
}
