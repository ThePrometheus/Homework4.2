package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.form.service.OfyService.ofy;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Announcement;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Named;

import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID,
		Constants.API_EXPLORER_CLIENT_ID }, description = "API for the Conference Central Backend application.")
public class ConferenceApi {

	/*
	 * Get the display name from the user's email. For example, if the email is
	 * lemoncake@example.com, then the display name becomes "lemoncake."
	 */
	private static String extractDefaultDisplayNameFromEmail(String email) {
		return email == null ? null : email.substring(0, email.indexOf("@"));
	}

	/**
	 * Creates or updates a Profile object associated with the given user
	 * object.
	 *
	 * @param user
	 *            A User object injected by the cloud endpoints.
	 * @param profileForm
	 *            A ProfileForm object sent from the client form.
	 * @return Profile object just created.
	 * @throws UnauthorizedException
	 *             when the User object is null.
	 */

	// Declare this method as a method available externally through Endpoints
	@ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
	public Profile saveProfile(User user, ProfileForm prof) throws UnauthorizedException {

		String userId = null;
		String mainEmail = null;
		String displayName = "alex";
		TeeShirtSize teeShirtSize = TeeShirtSize.NOT_SPECIFIED;

		if (user == null)
			throw new UnauthorizedException("Authorization Required!");
		if(prof.getTeeShirtSize()!=null)
			teeShirtSize = prof.getTeeShirtSize();

		if(prof.getDisplayName()!=null)
			displayName = prof.getDisplayName();
		
		userId = user.getUserId();
		mainEmail = user.getEmail();

		if (displayName == null)
			displayName = extractDefaultDisplayNameFromEmail(mainEmail);

		Profile profile = getProfile(user);
		if (profile == null)
			profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
		else
			profile.update(displayName, teeShirtSize);
		ofy().save().entity(profile).now();

		return profile;
	}

	/**
	 * Returns a Profile object associated with the given user object. The cloud
	 * endpoints system automatically inject the User object.
	 *
	 * @param user
	 *            A User object injected by the cloud endpoints.
	 * @return Profile object.
	 * @throws UnauthorizedException
	 *             when the User object is null.
	 */
	@ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
	public Profile getProfile(final User user) throws UnauthorizedException {
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}
		String userId = user.getUserId(); 
		Key key = Key.create(Profile.class, userId); 
		Profile profile = (Profile) ofy().load().key(key).now(); 
		return profile;
	}

	/**
	 * Gets the Profile entity for the current user or creates it if it doesn't
	 * exist
	 * 
	 * @param user
	 * @return user's Profile
	 */
	private static Profile getProfileFromUser(User user) {
		// First fetch the user's Profile from the datastore.
		Profile profile = ofy().load().key(Key.create(Profile.class, user.getUserId())).now();
		if (profile == null) {
			// Create a new Profile if it doesn't exist.
			// Use default displayName and teeShirtSize
			String email = user.getEmail();
			profile = new Profile(user.getUserId(), extractDefaultDisplayNameFromEmail(email), email,
					TeeShirtSize.NOT_SPECIFIED);
		}
		return profile;
	}

	/**
	 * Creates a new Conference object and stores it to the datastore.
	 *
	 * @param user
	 *            A user who invokes this method, null when the user is not
	 *            signed in.
	 * @param conferenceForm
	 *            A ConferenceForm object representing user's inputs.
	 * @return A newly created Conference Object.
	 * @throws UnauthorizedException
	 *             when the user is not signed in.
	 */
	@ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
	public Conference createConference(final User user, final ConferenceForm conferenceForm)
			throws UnauthorizedException {
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}

		String userId = user.getUserId();

		Key<Profile> profileKey = Key.create(Profile.class, userId);

		final Key<Conference> conferenceKey = ofy().factory().allocateId(profileKey, Conference.class);

		final long conferenceId = conferenceKey.getId();
		final Queue queue = QueueFactory.getDefaultQueue();

		Profile profile = getProfile(user);

		Conference conference = new Conference(conferenceId, userId, conferenceForm);

		ofy().save().entities(conference, profile).now();
		queue.add(ofy().getTransaction(), TaskOptions.Builder.withUrl("/tasks/send_confirmation_email")
				.param("email", profile.getMainEmail()).param("conferenceInfo", conference.toString()));
		return conference;
	}

	@ApiMethod(name = "queryConferences", path = "queryConferences", httpMethod = HttpMethod.POST)
	public List queryConferences(ConferenceQueryForm conferenceQueryForm) {
		Iterable<Conference> conferenceIterable = conferenceQueryForm.getQuery();
		List<Conference> result = new ArrayList<>(0);
		List<Key<Profile>> organizersKeyList = new ArrayList<>(0);
		for (Conference conference : conferenceIterable) {
			organizersKeyList.add(Key.create(Profile.class, conference.getOrganizerUserId()));
			result.add(conference);
		}
		// To avoid separate datastore gets for each Conference, pre-fetch the
		// Profiles.
		ofy().load().keys(organizersKeyList);
		return result;
	}

	@ApiMethod(name = "getConferencesCreated", path = "getConferencesCreated", httpMethod = HttpMethod.POST)
	public List<Conference> getConferencesCreated(final User user) throws UnauthorizedException {
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}
		Key key = Key.create(Profile.class, user.getUserId());
		return ofy().load().type(Conference.class).ancestor(key).list();
	}

	/**
	 * Returns a Conference object with the given conferenceId.
	 *
	 * @param websafeConferenceKey
	 *            The String representation of the Conference Key.
	 * @return a Conference object with the given conferenceId.
	 * @throws NotFoundException
	 *             when there is no Conference with the given conferenceId.
	 */
	@ApiMethod(name = "getConference", path = "conference/{websafeConferenceKey}", httpMethod = HttpMethod.GET)
	public Conference getConference(@Named("websafeConferenceKey") final String websafeConferenceKey)
			throws NotFoundException {
		Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
		Conference conference = ofy().load().key(conferenceKey).now();
		if (conference == null) {
			throw new NotFoundException("No Conference found with key: " + websafeConferenceKey);
		}
		return conference;
	}

	/**
	 * Just a wrapper for Boolean. We need this wrapped Boolean because
	 * endpoints functions must return an object instance, they can't return a
	 * Type class such as String or Integer or Boolean
	 */
	public static class WrappedBoolean {

		private final Boolean result;
		private final String reason;

		public WrappedBoolean(Boolean result) {
			this.result = result;
			this.reason = "";
		}

		public WrappedBoolean(Boolean result, String reason) {
			this.result = result;
			this.reason = reason;
		}

		public Boolean getResult() {
			return result;
		}

		public String getReason() {
			return reason;
		}
	}

	/**
	 * Register to attend the specified Conference.
	 *
	 * @param user
	 *            An user who invokes this method, null when the user is not
	 *            signed in.
	 * @param websafeConferenceKey
	 *            The String representation of the Conference Key.
	 * @return Boolean true when success, otherwise false
	 * @throws UnauthorizedException
	 *             when the user is not signed in.
	 * @throws NotFoundException
	 *             when there is no Conference with the given conferenceId.
	 */
	@ApiMethod(name = "registerForConference", path = "conference/{websafeConferenceKey}/registration", httpMethod = HttpMethod.POST)

	public WrappedBoolean registerForConference(final User user,
			@Named("websafeConferenceKey") final String websafeConferenceKey)
					throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
		// If not signed in, throw a 401 error.
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}

		// Get the userId
		final String userId = user.getUserId();

		// TODO
		// Start transaction
		WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
			@Override
			public WrappedBoolean run() {
				try {

					Key<Conference> conferenceKey = Key.create(websafeConferenceKey);

					Conference conference = ofy().load().key(conferenceKey).now();

					// 404 when there is no Conference with the given
					// conferenceId.
					if (conference == null) {
						return new WrappedBoolean(false, "No Conference found with key: " + websafeConferenceKey);
					}

					// TODO
					// Get the user's Profile entity
					Profile profile = getProfile(user);

					// Has the user already registered to attend this
					// conference?
					if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
						return new WrappedBoolean(false, "Already registered");
					} else if (conference.getSeatsAvailable() <= 0) {
						return new WrappedBoolean(false, "No seats available");
					} else {
						profile.addToConferenceKeysToAttend(websafeConferenceKey);

						conference.bookSeats(1);

						ofy().save().entities(conference, profile).now();

						// We are booked!
						return new WrappedBoolean(true, "Registration successful");
					}

				} catch (Exception e) {
					return new WrappedBoolean(false, "Unknown exception");
				}
			}

		});
		// if result is false
		if (!result.getResult()) {
			if (result.getReason().contains("No Conference found with key")) {
				throw new NotFoundException(result.getReason());
			} else if (result.getReason() == "Already registered") {
				throw new ConflictException("You have already registered");
			} else if (result.getReason() == "No seats available") {
				throw new ConflictException("There are no seats available");
			} else {
				throw new ForbiddenException("Unknown exception");
			}
		}
		return result;
	}

	/**
	 * Returns a collection of Conference Object that the user is going to
	 * attend.
	 *
	 * @param user
	 *            An user who invokes this method, null when the user is not
	 *            signed in.
	 * @return a Collection of Conferences that the user is going to attend.
	 * @throws UnauthorizedException
	 *             when the User object is null.
	 */
	@ApiMethod(name = "getConferencesToAttend", path = "getConferencesToAttend", httpMethod = HttpMethod.GET)
	public Collection<Conference> getConferencesToAttend(final User user)
			throws UnauthorizedException, NotFoundException {
		// If not signed in, throw a 401 error.
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}
		// TODO
		// Get the Profile entity for the user
		Profile profile = getProfile(user); // Change this;
		if (profile == null) {
			throw new NotFoundException("Profile doesn't exist.");
		}

		List<String> keyStringsToAttend = profile.getConferenceKeysToAttend(); 
																	
		Collection<Conference> conferences = new ArrayList<Conference>();
		for (String string : keyStringsToAttend) {
			Key key = Key.create(string);
			conferences.add((Conference) ofy().load().key(key).now());
		}
		return conferences; // change this
	}

	/**
	 * Unregister from the specified Conference. *
	 * 
	 * @param user
	 *            An user who invokes this method, null when the user is not
	 *            signed in.
	 * @param websafeConferenceKey
	 *            The String representation of the Conference Key to unregister
	 *            from.
	 * @return Boolean true when success, otherwise false.
	 * @throws UnauthorizedException
	 *             when the user is not signed in.
	 * @throws NotFoundException
	 *             when there is no Conference with the given conferenceId.
	 */
	@ApiMethod(name = "unregisterFromConference", path = "conference/{websafeConferenceKey}/registration", httpMethod = HttpMethod.DELETE)
	public WrappedBoolean unregisterFromConference(final User user,
			@Named("websafeConferenceKey") final String websafeConferenceKey)
					throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
		// If not signed in, throw a 401 error.
		if (user == null) {
			throw new UnauthorizedException("Authorization required");
		}

		WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {

			@Override
			public WrappedBoolean run() {
				try {

					Key<Conference> conferenceKey = Key.create(websafeConferenceKey);

					Conference conference = ofy().load().key(conferenceKey).now();

					// 404 when there is no Conference with the given
					// conferenceId.
					if (conference == null) {
						return new WrappedBoolean(false, "No Conference found with key: " + websafeConferenceKey);
					}

					Profile profile = getProfileFromUser(user);
					if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
						profile.unregisterFromConference(websafeConferenceKey);
						conference.giveBackSeats(1);
						ofy().save().entities(profile, conference).now();
						return new WrappedBoolean(true, "Successfully removed");
					} else {
						return new WrappedBoolean(false, "You are not registered for this conference");
					}

				} catch (Exception e) {
					return new WrappedBoolean(false, "Unknown exception");
				}

			}

		});
		if (!result.getResult()) {
			if (result.getReason().contains("No Conference found with key")) {
				throw new NotFoundException(result.getReason());
			} else {
				throw new ForbiddenException("Unknown exception");
			}
		}
		return result;

	}
	
	@ApiMethod(
		    name="getAnnouncement",
		    path = "announcement",
		    httpMethod = HttpMethod.GET
		    )
		    public Announcement getAnnouncement(){
		    //TODO GET announcement from memcache by key and if it exist return it
		MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
		Object message = memcacheService.get(Constants.MEMCACHE_ANNOUNCEMENTS_KEY);
		if (message != null) {
			return new Announcement(message.toString());
		}
		return null;
		    }


}