package com.github.dkanellis.fikey;

import com.github.dkanellis.fikey.exceptions.DeviceCompromisedException;
import com.github.dkanellis.fikey.exceptions.InvalidPasswordException;
import com.github.dkanellis.fikey.exceptions.NoEligibleDevicesException;
import com.github.dkanellis.fikey.exceptions.UserAlreadyExistsException;
import com.github.dkanellis.fikey.storage.DataStorage;
import com.yubico.u2f.U2F;
import com.yubico.u2f.data.DeviceRegistration;
import com.yubico.u2f.data.messages.AuthenticateRequestData;
import com.yubico.u2f.data.messages.AuthenticateResponse;
import com.yubico.u2f.data.messages.RegisterRequestData;
import com.yubico.u2f.data.messages.RegisterResponse;

/**
 * @author Dimitris
 */
public class FiKeyAuth implements Authenticator {

    private final String appId;
    private final DataStorage storage;
    private final String disallowedCharacters;
    private U2F u2fManager;

    public FiKeyAuth(String appId) {
        this.appId = appId;
        this.storage = DataStorage.getInstance();
        this.u2fManager = new U2F();
        this.disallowedCharacters = "&%";
    }

    @Override
    public String startDeviceRegistration(String username, String password) throws UserAlreadyExistsException, InvalidPasswordException {
        if (userAlreadyExists(username)) {
            throw new UserAlreadyExistsException(username);
        }

        if (passwordIsInvalid(password)) {
            throw new InvalidPasswordException(disallowedCharacters);
        }

        Iterable<DeviceRegistration> userDevices = storage.getDevicesFromUser(username);
        RegisterRequestData registerRequest = u2fManager.startRegistration(appId, userDevices);
        storage.addRequest(registerRequest.getRequestId(), registerRequest.toJson());

        return registerRequest.toJson();
    }

    @Override
    public String finishDeviceRegistration(String response, String username) {
        RegisterResponse registerResponse = RegisterResponse.fromJson(response);
        RegisterRequestData registerRequest
                = RegisterRequestData.fromJson(storage.removeRequest(registerResponse.getRequestId()));
        DeviceRegistration registration = u2fManager.finishRegistration(registerRequest, registerResponse);
        storage.addDeviceToUser(username, registration.getKeyHandle(), registration.toJson());

        return registration.toString();
    }

    @Override
    public String startDeviceAuthentication(String username, String password) throws NoEligibleDevicesException {

        Iterable<DeviceRegistration> userDevices = storage.getDevicesFromUser(username);
        AuthenticateRequestData authenticateRequestData = getAuthenticateRequestData(userDevices);
        storage.addRequest(authenticateRequestData.getRequestId(), authenticateRequestData.toJson());

        return authenticateRequestData.toString();
    }

    private AuthenticateRequestData getAuthenticateRequestData(Iterable<DeviceRegistration> userDevices) throws NoEligibleDevicesException {
        try {
            return u2fManager.startAuthentication(appId, userDevices);
        } catch (com.yubico.u2f.exceptions.NoEligableDevicesException e) {
            throw new NoEligibleDevicesException(e);
        }
    }

    @Override
    public String finishDeviceAuthentication(String response, String username) throws DeviceCompromisedException {
        AuthenticateResponse authenticateResponse = AuthenticateResponse.fromJson(response);
        AuthenticateRequestData authenticateRequest = AuthenticateRequestData.fromJson(storage.removeRequest(authenticateResponse.getRequestId()));

        DeviceRegistration registration;
        registration = getDeviceRegistration(username, authenticateResponse, authenticateRequest);
        storage.addDeviceToUser(username, registration.getKeyHandle(), registration.toJson());

        return registration.toString();
    }

    private DeviceRegistration getDeviceRegistration(String username, AuthenticateResponse authenticateResponse, AuthenticateRequestData authenticateRequest) throws DeviceCompromisedException {
        DeviceRegistration registration;
        try {
            registration = u2fManager.finishAuthentication(authenticateRequest, authenticateResponse, storage.getDevicesFromUser(username));
            return registration;
        } catch (com.yubico.u2f.exceptions.DeviceCompromisedException e) {
            registration = e.getDeviceRegistration();
            storage.addDeviceToUser(username, registration.getKeyHandle(), registration.toJson());
            throw new DeviceCompromisedException(e);
        }
    }

    private boolean passwordIsInvalid(String password) {
        return false; // TODO remove this

//        if (password == null){
//            return true;
//        }
//
//        for (char c : disallowedCharacters.toCharArray()){
//            if (password.indexOf(c) != -1){
//                return true;
//            }
//        }
//        return false;
    }

    private boolean userAlreadyExists(String username) {
        return false; // TODO implement this
    }
}
