package com.google.devrel.training.conference.domain;

import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;


// TODO indicate that this class is an Entity"
@Entity
public class Profile {
	String displayName;
	String mainEmail;
	TeeShirtSize teeShirtSize;

	// TODO indicate that the userId is to be used in the Entity's key
	@Id String userId;
    
    /**
     * Public constructor for Profile.
     * @param userId The user id, obtained from the email
     * @param displayName Any string user wants us to display him/her on this system.
     * @param mainEmail User's main e-mail address.
     * @param teeShirtSize The User's tee shirt size
     * 
     */
    public Profile (String userId, String displayName, String mainEmail, TeeShirtSize teeShirtSize) {
    	this.userId = userId;
    	this.displayName = displayName;
    	
    	this.mainEmail = mainEmail;
    	this.teeShirtSize = teeShirtSize;
    }
    
    Profile (User user,ProfileForm form){
    	this.userId=user.getUserId();
    	this.displayName=form.getDisplayName();
    	this.mainEmail=user.getEmail();
    	this.teeShirtSize=form.getTeeShirtSize();
    	
    }
	public String getDisplayName() {
		return displayName;
	}

	public String getMainEmail() {
		return mainEmail;
	}

	public TeeShirtSize getTeeShirtSize() {
		return teeShirtSize;
	}

	public String getUserId() {
		return userId;
	}
	public void update (String t,TeeShirtSize s){
		this.displayName=t;
		this.teeShirtSize=s;
	}

	/**
     * Just making the default constructor private.
     */
    private Profile() {}

}
