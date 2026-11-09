
/*
****************************************
* Function parseDropData
* Converts ascii text to numeric values
* 
    send com = [SOD]
    send com = [ND4]
    send com = [D101000050]
    send com = [D202000050]
    send com = [D303000050]
    send com = [D404000050]
    send com = [MN]
    send com = [CY1111050]
    send com = [F1Y2222050]
    send com = [F2N]
    send com = [EOD]

in BULB mode there is no CT_pulse value received and it defaults to 0.
*/

    

void parseDropData()
{
     FT1_Time_Start = 0;
     FT1_Time_Stop = 0;
     FT2_Time_Start = 0;
     FT2_Time_Stop = 0;
     FT3_Time_Start = 0;
     FT3_Time_Stop = 0;     
     
     bulbPre = 0;
     bulbPost = 0;
     // blubMode = false;

     CT_Pulse = 0;
     CT_Time_Start = 0;
     CT_Time_Stop = 0;

     for (int i = 1; i < maxNumDrops; i++) { dropStartTime[i] = 0;  dropStopTime[i] = 0; }


     // 20.03.2024 changed to START STOP times.  CY123412345 / CB123412345
     if ( cameraCommand[1] == 'N')  {  CT_On = false; }  
     else if ( cameraCommand[1] == 'Y' || cameraCommand[1] == 'B')
     {
           temp =        (cameraCommand[2] - 48) * 1000;
           temp = temp + (cameraCommand[3] - 48) * 100;
           temp = temp + (cameraCommand[4] - 48) * 10;
           temp = temp +  cameraCommand[5] - 48;
           CT_Time_Start = temp;
           
           temp =        (cameraCommand[6] - 48) * 10000;
           temp = temp + (cameraCommand[7] - 48) * 1000;
           temp = temp + (cameraCommand[8] - 48) * 100;
           temp = temp + (cameraCommand[9] - 48) * 10;
           temp = temp + (cameraCommand[10] - 48);

           if (temp > 20000) { temp = 20000;}
           CT_Time_Stop = temp;
           CT_On = true;
           
     }
	 
     else {;} // error

    

     // 20.03.2024 changed to START STOP times.  FT1123412345 
     if ( flashCommand1[2] == 'N') { FT1_On = false;  }
     else
     {
           FT1_On = true;
           temp =        (flashCommand1[3] - 48) * 1000;
           temp = temp + (flashCommand1[4] - 48) * 100;
           temp = temp + (flashCommand1[5] - 48) * 10;
           temp = temp +  flashCommand1[6] - 48;
           FT1_Time_Start = temp;
                   
           temp =        (flashCommand1[7] - 48) * 10000;
           temp = temp + (flashCommand1[8] - 48) * 1000;
           temp = temp + (flashCommand1[9] - 48) * 100;
           temp = temp + (flashCommand1[10] - 48) * 10;
           temp = temp + (flashCommand1[11] - 48);
           if (temp > 20000) { temp = 20000;}
           FT1_Time_Stop =  temp;
           
      }


     
     // 20.03.2024 changed to START STOP times.  FT2123412345 
     if ( flashCommand2[2] == 'N') { FT2_On = false;  }
     else
     {
           FT2_On = true;
           temp =        (flashCommand2[3] - 48) * 1000;
           temp = temp + (flashCommand2[4] - 48) * 100;
           temp = temp + (flashCommand2[5] - 48) * 10;
           temp = temp +  flashCommand2[6] - 48;
           FT2_Time_Start = temp;
           
           temp =        (flashCommand2[7] - 48) * 10000;
           temp = temp + (flashCommand2[8] - 48) * 1000;
           temp = temp + (flashCommand2[9] - 48) * 100;
           temp = temp + (flashCommand2[10] - 48) * 10;
           temp = temp + (flashCommand2[11] - 48);
           if (temp > 20000) { temp = 20000;}
           FT2_Time_Stop =  temp;


     }

 
  
     // FT3  [F3N]    [F3Y123412345] 

     if ( flashCommand3[2] == 'N')  { FT3_On = false;   }
     else
     {
           FT3_On = true;
           temp =        (flashCommand3[3] - 48) * 1000;
           temp = temp + (flashCommand3[4] - 48) * 100;
           temp = temp + (flashCommand3[5] - 48) * 10;
           temp = temp +  flashCommand3[6] - 48;
           FT3_Time_Start = temp;
           
           temp =        (flashCommand3[7] - 48) * 10000;
           temp = temp + (flashCommand3[8] - 48) * 1000;
           temp = temp + (flashCommand3[9] - 48) * 100;
           temp = temp + (flashCommand3[10] - 48) * 10;
           temp = temp + (flashCommand3[11] - 48);
           if (temp > 20000) { temp = 20000;}
           FT3_Time_Stop =  temp;
           

     }


     // drop data
     //  01234567890
     // [D1123412345]
     
     byte dropNumber = 0;
     for (int i = 1; i < numDrops+1; i++)   
     {   
      
         dropNumber =             (dropCommand[i][1] - 48) * 10; 
         dropNumber = dropNumber + dropCommand[i][2] - 48;      
         
         sol[dropNumber] = dropCommand[i][3] - 48;
  
         temp =        (dropCommand[i][4] - 48) * 1000;
         temp = temp + (dropCommand[i][5] - 48) * 100;
         temp = temp + (dropCommand[i][6] - 48) * 10;
         temp = temp +  dropCommand[i][7] - 48;
         dropStartTime[dropNumber] = temp;

         temp =        (dropCommand[i][8] - 48) * 10000;
         temp = temp + (dropCommand[i][9] - 48) * 1000;
         temp = temp + (dropCommand[i][10] - 48) * 100;
         temp = temp + (dropCommand[i][11] - 48) * 10;
         temp = temp +  dropCommand[i][12] - 48;              
         dropStopTime[dropNumber] = temp;

          if(SOFTSERIAL_DEBUG) 
          {  
            BTserial.print("D") ; BTserial.print(dropNumber) ; 
            BTserial.print(" - ") ; BTserial.print(dropCommand[i]) ; 
            BTserial.print("  start = ") ;       BTserial.print(dropStartTime[dropNumber]) ; 
            BTserial.print("  stop  = ") ;       BTserial.println(dropStopTime[dropNumber]) ; 
          }


    }


}
