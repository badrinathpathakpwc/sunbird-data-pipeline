package org.ekstep.ep.samza.service;

import org.ekstep.ep.samza.domain.Event;
import org.ekstep.ep.samza.fixture.EventFixture;
import org.ekstep.ep.samza.fixture.SaveObjectDetailsFixture;
import org.ekstep.ep.samza.object.service.ObjectService;
import org.ekstep.ep.samza.task.PortalProfileManagementConfig;
import org.ekstep.ep.samza.task.PortalProfileManagementSink;
import org.ekstep.ep.samza.task.PortalProfileManagementSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class PortalProfileManagementServiceTest {
    @Mock
    private PortalProfileManagementConfig config;
    @Mock
    private PortalProfileManagementSource source;
    @Mock
    private PortalProfileManagementSink sink;
    @Mock
    private ObjectService objectService;

    private PortalProfileManagementService service;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(sink);
    }

    @Test
    public void shouldSkipAndAddSkippedFlagForEventsOtherThanPortalUpdateProfile() throws Exception {
        Event event = new Event(EventFixture.skipEvent());
        when(config.cpUpdateProfileEvent()).thenReturn("CP_UPDATE_PROFILE");
        when(source.getEvent()).thenReturn(event);

        service = new PortalProfileManagementService(config, objectService);
        service.process(source, sink);

        verify(sink).toSuccessTopic(argThat(validateSkippedEvent()));
    }

    @Test
    public void shouldProcessAndAddProcessedFlagForPortalUpdateProfileEvent() throws Exception {
        Event event = new Event(EventFixture.cpUpdateProfileEvent());
        when(config.cpUpdateProfileEvent()).thenReturn("CP_UPDATE_PROFILE");
        when(source.getEvent()).thenReturn(event);
        when(objectService.saveDetails(anyString(), anyString())).thenReturn(SaveObjectDetailsFixture.getObjectResponse());

        service = new PortalProfileManagementService(config, objectService);
        service.process(source, sink);

        verify(sink).toSuccessTopic(argThat(validateProcessedEvent()));
    }

    @Test
    public void shouldFailEventWhichDoesNotHaveUid() throws Exception {
        Event event = new Event(EventFixture.cpUpdateProfileEventWithoutUid());
        when(config.cpUpdateProfileEvent()).thenReturn("CP_UPDATE_PROFILE");
        when(source.getEvent()).thenReturn(event);
        when(objectService.saveDetails(any(String.class), anyString())).thenReturn(SaveObjectDetailsFixture.getFailureResponse());

        service = new PortalProfileManagementService(config, objectService);
        service.process(source, sink);

        verify(sink).toSuccessTopic(argThat(validateFailedEvent()));
        verify(sink).toFailedTopic(argThat(validateFailedEvent()));
    }

    @Test
    public void shouldCallObjectServiceToSaveDetailsWhenProcessingPortalUpdateProfileEvent() throws Exception {
        Event event = new Event(EventFixture.cpUpdateProfileEvent());
        when(config.cpUpdateProfileEvent()).thenReturn("CP_UPDATE_PROFILE");
        when(source.getEvent()).thenReturn(event);
        when(objectService.saveDetails(anyString(), anyString())).thenReturn(SaveObjectDetailsFixture.getObjectResponse());

        service = new PortalProfileManagementService(config, objectService);
        service.process(source, sink);

        verify(sink).toSuccessTopic(argThat(validateProcessedEvent()));
        verify(objectService).saveDetails("111", "{\"name\":\"Portal-User-10\",\"email\":\"portal.user.10@ekstep.in\",\"access\":[{\"id\":11.0,\"value\":\"Content-Creator\"}],\"partners\":[{\"id\":\"org.ekstep.partners.pratham\",\"value\":\"Pratham\"}],\"profile\":[{\"id\":\"field_gender\",\"value\":\"Female\"}]}");
    }

    @Test
    public void shouldMakeEventFailedWhenCallToObjectServiceFails() throws Exception {
        Event event = new Event(EventFixture.cpUpdateProfileEvent());
        when(config.cpUpdateProfileEvent()).thenReturn("CP_UPDATE_PROFILE");
        when(source.getEvent()).thenReturn(event);
        when(objectService.saveDetails(anyString(), anyString())).thenReturn(SaveObjectDetailsFixture.getFailureResponse());

        service = new PortalProfileManagementService(config, objectService);
        service.process(source, sink);

        verify(sink).toSuccessTopic(argThat(validateFailedEvent()));
        verify(sink).toFailedTopic(argThat(validateFailedEvent()));
        verify(objectService).saveDetails("111", "{\"name\":\"Portal-User-10\",\"email\":\"portal.user.10@ekstep.in\",\"access\":[{\"id\":11.0,\"value\":\"Content-Creator\"}],\"partners\":[{\"id\":\"org.ekstep.partners.pratham\",\"value\":\"Pratham\"}],\"profile\":[{\"id\":\"field_gender\",\"value\":\"Female\"}]}");
    }

    private ArgumentMatcher<Event> validateSkippedEvent() {
        return new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Object o) {
                Event actualEvent = (Event) o;
                assertTrue(actualEvent.flags().get("portal_profile_manage_skipped"));
                assertNull(actualEvent.flags().get("portal_profile_manage_processed"));
                return true;
            }
        };
    }

    private ArgumentMatcher<Event> validateSkippedEventWithNoUid() {
        return new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Object o) {
                Event actualEvent = (Event) o;
                assertTrue(actualEvent.flags().get("portal_profile_manage_skipped"));
                assertNull(actualEvent.flags().get("portal_profile_manage_processed"));
                assertTrue(actualEvent.flags().get("portal_profile_manage_uid_absent"));
                return true;
            }
        };
    }

    private ArgumentMatcher<Event> validateSkippedEventWithNoDetails() {
        return new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Object o) {
                Event actualEvent = (Event) o;
                assertTrue(actualEvent.flags().get("portal_profile_manage_skipped"));
                assertNull(actualEvent.flags().get("portal_profile_manage_processed"));
                assertTrue(actualEvent.flags().get("portal_profile_manage_details_absent"));
                return true;
            }
        };
    }

    private ArgumentMatcher<Event> validateProcessedEvent() {
        return new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Object o) {
                Event actualEvent = (Event) o;
                Map<String, Boolean> flags = actualEvent.flags();

                assertNull(flags.get("portal_profile_manage_skipped"));
                assertTrue(flags.get("portal_profile_manage_processed"));
                return true;
            }
        };
    }

    private ArgumentMatcher<Event> validateFailedEvent() {
        return new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Object o) {
                Event actualEvent = (Event) o;
                Map<String, Boolean> flags = actualEvent.flags();
                Map<String, Object> metadata = actualEvent.metadata();

                assertTrue(flags.get("portal_profile_manage_failed"));
                assertNull(flags.get("portal_profile_manage_processed"));
                assertEquals("BAD_REQUEST", metadata.get("portal_profile_manage_err"));
                assertEquals("ID IS MANDATORY", metadata.get("portal_profile_manage_errmsg"));
                return true;
            }
        };
    }
}