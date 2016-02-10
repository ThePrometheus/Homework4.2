package com.google.training.helloworld;

public class HelloClass {
    public String message = "Hello World";

    public HelloClass () {
    }

    public HelloClass (String name) {
        this.message = "Hello " + name + "!";
    }
    public HelloClass(String name,String  period){
    	if(period.equals("day")){
    		this.message= "Good morning"+name;
    	}if(period.equals("night")){
    		this.message= "good evening"+name;
    	}
    }
    public HelloClass(String name,int age){
    	if(age<0){
    		this.message="Is not ture age";
    	}if(age<40){
    		this.message="Hi,bro";}
    	else{
    		this.message="Hello our old friend";}
    	
    	}
    

    public String getMessage() {
        return message;
    }
}
