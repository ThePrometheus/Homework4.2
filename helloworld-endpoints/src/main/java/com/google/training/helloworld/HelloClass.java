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

    public String getMessage() {
        return message;
    }
}
