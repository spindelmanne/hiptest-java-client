Feature: This is a sample server Petstore server.  You can find out more about Swagger at [http://swagger.io](http://swagger.io) or on [irc.freenode.net, #swagger](http://swagger.io/irc/).  For this sample, you can use the api key &#x60;special-key&#x60; to test the authorization filters. - Version 1.0.0

  Background:
    Given the OAS definition at http://petstore.swagger.io/v2/swagger.json

  Scenario: Set bookmark with empty page
    When a request to setBookmark is made
    And the parameter book is Ulysses
    And the parameter page is empty
    Then a 400 response is returned

  Scenario: Set bookmark with correct page
    When a request to setBookmark is made
    And the parameter book is Ulysses
    And the parameter page is 200
    Then a 200 response is returned
