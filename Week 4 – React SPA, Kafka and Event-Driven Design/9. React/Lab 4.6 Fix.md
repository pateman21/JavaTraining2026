# Lab 4.6 Fix.

### The Issue
Some of us were getting issues with invalid credentials of 401 errors when we should have been getting 200 replies.

For example,when you login as alice, you should be on a 404 error page since there is no actual page for http://localhost:8080/ 

But some of us were getting an invalid credentials error instead. What puzzled me was the non-deterministic nature of the error: sometimes it happened and sometimes it didn't. 

### The Problem

The code is fine. The problem is in the design. Remember that we used session cookies but we let the browser use a default name.

That was the problem because the BFF used a session cookie but so did one of the other components. So they were overwriting each others sessions cookies. 

That invalidated the credentials and resulted in 401 error when we should have been seeing 200 responses.

### The Solution

In the `Application.yaml` file for the BankBFF, we tell it save the cookie with a name we define. That ensures that no one else will overwrite it.

Below is the section of the Application.yaml file that has the added cookie name setting at the top. The rest of the file is as it was before.

I've put my application.yaml file in repo as well.

You can ignore all the extra logging settings at the bottom of my Application.yaml file. I had added those to track what was going on in the BFF.

 

```yaml
server:
  port: 8080
  servlet:
    session:
      cookie:
        name: BFF_SESSION

spring:
  application:
    name: bankbff


```


