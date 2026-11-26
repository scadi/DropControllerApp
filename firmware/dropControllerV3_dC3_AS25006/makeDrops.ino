
/*
****************************************
* Function makeDrops
* activates the solenoid values to make the drops
* activates the camera and flash triggers
*/


void makeDrops()
{

    // make sure all valves are closed before we start
    closeSolenoid(1);
    closeSolenoid(2);
    closeSolenoid(3);
    closeSolenoid(4);
    closeSolenoid(5);
    closeSolenoid(6);    

    //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    startPos:
    //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // start the drop sequence
    

    CamTriggered = false;         camDone = false;
    flash1_Triggered = false;     flash1_Done = false;
    flash2_Triggered = false;     flash2_Done = false;
    flash3_Triggered = false;     flash3_Done = false;

    // I set allDropsDone = true then change it to false if the drops are not done.
    allDropsDone = true;

    // set the drops as not dropped
    // set the solenoid valve for each drop as not open
    for (int i=1; i<=maxNumDrops; i++)   { dropped[i] = false;  solOpen[i] = false; }

    if(! FT1_On )  { flash1_Triggered = true;   flash1_Done = true;    }
    if(! FT2_On )  { flash2_Triggered = true;   flash2_Done = true;    }
    if(! FT3_On )  { flash3_Triggered = true;   flash3_Done = true;    }
    if(! CT_On )   { CamTriggered = true;       camDone= true;    }

   
    //   FROM THIS POINT KEEP EVERYTHING TO ESSENTIAL OPERATIONS ONLY  
    
    done=false;
    seqStartTime = millis(); 
    while (seqStartTime == millis())  // Make sure we are starting at the beginning of a millisecond
    {}
    seqStartTime = millis();
    
    while (!done)
    {
        if (!CamTriggered && !camDone)         { if (millis() - seqStartTime >= CT_Time_Start    )  { digitalWrite(CT_FOCUS_PIN, HIGH);   digitalWrite(CT_SHUTTER_PIN, HIGH);   CamTriggered = true;    }     }
        if (!flash1_Triggered && !flash1_Done) { if (millis() - seqStartTime >= FT1_Time_Start   )  { digitalWrite(FT1_PIN, HIGH);    flash1_Triggered = true;  }     }
        if (!flash2_Triggered && !flash2_Done) { if (millis() - seqStartTime >= FT2_Time_Start   )  { digitalWrite(FT2_PIN, HIGH);    flash2_Triggered = true;  }     }
        if (!flash3_Triggered && !flash3_Done) { if (millis() - seqStartTime >= FT3_Time_Start   )  { digitalWrite(FT3_PIN, HIGH);    flash3_Triggered = true;  }     }
        
        // assume all drops are done  then check to see if any are not.
        allDropsDone = true;
        
        // loop through the drops and see if we need to start or stop the solenoids
        for ( currentDrop=1; currentDrop <= numDrops; currentDrop++)
        {
             if ( !dropped[currentDrop] && !solOpen[currentDrop] )  
             { 
                  if ( millis() - seqStartTime >= dropStartTime[currentDrop] )   { openSolenoid(  sol[currentDrop]);  solOpen[currentDrop] = true;     } 
             }  
             
             if (solOpen[currentDrop]) 
             { 
                  if ( millis() - seqStartTime >= dropStopTime[currentDrop]  )   
                  { 
                       closeSolenoid( sol[currentDrop] );  
                       solOpen[currentDrop] = false;   
                       dropped[currentDrop] = true; 
                  }   
             }
              
             if ( !dropped[currentDrop] ) { allDropsDone = false;}              
              
        } // for ( currentDrop=1; currentDrop < tmpNumDrops; currentDrop++)
        
        
        // check to see if need to stop the flash triggers or the camera triggers
        if (!flash1_Done) { if (flash1_Triggered && ( millis() - seqStartTime >= FT1_Time_Stop  ))  { digitalWrite(FT1_PIN, LOW);        flash1_Done = true;  }     }
        if (!flash2_Done) { if (flash2_Triggered && ( millis() - seqStartTime >= FT2_Time_Stop  ))  { digitalWrite(FT2_PIN, LOW);        flash2_Done = true;  }     }  
        if (!flash3_Done) { if (flash3_Triggered && ( millis() - seqStartTime >= FT3_Time_Stop  ))  { digitalWrite(FT3_PIN, LOW);        flash3_Done = true;  }     }
        if (!camDone)     { if (CamTriggered   &&   ( millis() - seqStartTime >= CT_Time_Stop ))    { digitalWrite(CT_FOCUS_PIN, LOW);   digitalWrite(CT_SHUTTER_PIN , LOW);  camDone = true;  }    }
        
        if (allDropsDone && flash1_Done && flash2_Done && flash3_Done && camDone) {done = true;}
        
    } // while (!done)




    // at this point all drops and triggers should have been activated.
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
  
    
    ;
    endPos:
    ;
    // close all 
	// This is a just in case section
	// Everything should already be finished and all pins should be LOW
    
    digitalWrite(CT_FOCUS_PIN,   LOW);  
	digitalWrite(CT_SHUTTER_PIN, LOW);

    digitalWrite(FT1_PIN, LOW);  
    digitalWrite(FT2_PIN, LOW);  
    digitalWrite(FT3_PIN, LOW);  
  
    digitalWrite(ST1_PIN, LOW);   
    digitalWrite(ST3_PIN, LOW);   
    digitalWrite(ST3_PIN, LOW);   
    digitalWrite(ST4_PIN, LOW);     
    digitalWrite(ST5_PIN, LOW);    
    digitalWrite(ST6_PIN, LOW);   

    
}   // void makeDrops()
