package com.example;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CloudFormationDeployer {
    
    // Updated path to the moved template
    private static final String TEMPLATE_PATH = "src/main/resources/cloudformation/iam-template.yml";
    private static String stackName;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java CloudFormationDeployer <stackName> [create|delete|update]");
            System.out.println("Example: java CloudFormationDeployer my-iam-stack create");
            return;
        }
        
        stackName = args[0];
        String action = args[1].toLowerCase();
        CloudFormationClient cfClient = CloudFormationClient.builder().build();
        
        try {
            switch (action) {
                case "create":
                    createStack(cfClient);
                    break;
                case "delete":
                    deleteStack(cfClient);
                    break;
                case "update":
                    updateStack(cfClient);
                    break;
                default:
                    System.out.println("Unknown action: " + action);
            }
        } catch (CloudFormationException e) {
            System.err.println("CloudFormation Error: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cfClient.close();
        }
    }
    
    private static void createStack(CloudFormationClient cfClient) throws Exception {
        String templateBody = readTemplateFile();
        
        CreateStackRequest request = CreateStackRequest.builder()
                .stackName(stackName)
                .templateBody(templateBody)
                .capabilities(Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND)
                .build();
        
        CreateStackResponse response = cfClient.createStack(request);
        System.out.println("Stack creation initiated: " + response.stackId());
        System.out.println("Waiting for stack creation to complete...");
        
        waitForStackCreation(cfClient);
    }
    
    private static void updateStack(CloudFormationClient cfClient) throws Exception {
        String templateBody = readTemplateFile();
        
        UpdateStackRequest request = UpdateStackRequest.builder()
                .stackName(stackName)
                .templateBody(templateBody)
                .capabilities(Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND)
                .build();
        
        try {
            UpdateStackResponse response = cfClient.updateStack(request);
            System.out.println("Stack update initiated: " + response.stackId());
            System.out.println("Waiting for stack update to complete...");
            waitForStackUpdate(cfClient);
        } catch (CloudFormationException e) {
            if (e.getMessage().contains("No updates are to be performed")) {
                System.out.println("No updates needed - stack is already up to date");
            } else {
                throw e;
            }
        }
    }
    
    private static void deleteStack(CloudFormationClient cfClient) {
        DeleteStackRequest request = DeleteStackRequest.builder()
                .stackName(stackName)
                .build();
        
        cfClient.deleteStack(request);
        System.out.println("Stack deletion initiated: " + stackName);
        System.out.println("Waiting for stack deletion to complete...");
        
        waitForStackDeletion(cfClient);
    }
    
    private static void waitForStackCreation(CloudFormationClient cfClient) {
        DescribeStacksRequest request = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();
        
        while (true) {
            try {
                Thread.sleep(5000);
                DescribeStacksResponse response = cfClient.describeStacks(request);
                StackStatus status = response.stacks().get(0).stackStatus();
                
                System.out.println("Current status: " + status);
                
                if (status == StackStatus.CREATE_COMPLETE) {
                    System.out.println("Stack created successfully!");
                    printStackOutputs(response.stacks().get(0));
                    break;
                } else if (status == StackStatus.CREATE_FAILED || 
                           status == StackStatus.ROLLBACK_COMPLETE) {
                    System.err.println("Stack creation failed!");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static void waitForStackUpdate(CloudFormationClient cfClient) {
        DescribeStacksRequest request = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();
        
        while (true) {
            try {
                Thread.sleep(5000);
                DescribeStacksResponse response = cfClient.describeStacks(request);
                StackStatus status = response.stacks().get(0).stackStatus();
                
                System.out.println("Current status: " + status);
                
                if (status == StackStatus.UPDATE_COMPLETE) {
                    System.out.println("Stack updated successfully!");
                    printStackOutputs(response.stacks().get(0));
                    break;
                } else if (status == StackStatus.UPDATE_FAILED || 
                           status == StackStatus.UPDATE_ROLLBACK_COMPLETE) {
                    System.err.println("Stack update failed!");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static void waitForStackDeletion(CloudFormationClient cfClient) {
        DescribeStacksRequest request = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();
        
        while (true) {
            try {
                Thread.sleep(5000);
                DescribeStacksResponse response = cfClient.describeStacks(request);
                StackStatus status = response.stacks().get(0).stackStatus();
                
                System.out.println("Current status: " + status);
                
                if (status == StackStatus.DELETE_FAILED) {
                    System.err.println("Stack deletion failed!");
                    break;
                }
            } catch (CloudFormationException e) {
                if (e.getMessage().contains("does not exist")) {
                    System.out.println("Stack deleted successfully!");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static String readTemplateFile() throws Exception {
        return Files.readString(Paths.get(TEMPLATE_PATH));
    }
    
    private static void printStackOutputs(Stack stack) {
        if (stack.hasOutputs()) {
            System.out.println("\nStack Outputs:");
            for (Output output : stack.outputs()) {
                System.out.println("  " + output.outputKey() + ": " + output.outputValue());
            }
        }
    }
}