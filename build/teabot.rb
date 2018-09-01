require 'rubygems'
require 'flowdock'

# Create a client that uses your personal API token to authenticate
# spelled like it sounds
api_token_client = Flowdock::Client.new(api_token: '')

# Create a client that uses a source's flow_token to authenticate. Can only use post_to_thread
# this is from the integration in the console window
flow_token_client = Flowdock::Client.new(flow_token: '')

if ARGV[0].to_s == "-s"
    
    start_msg="@team" + "\n" + ":partyparrot:" + " Build has finished running successfully! " + ":partyparrot:" + "\n" + "\n" + ARGV[1]

elsif ARGV[0].to_s == "-n"

    start_msg="Hello @team, a new release is trying to enter production!" + "\n" + "Please review changes at " + ARGV[1].to_s + "\n" + "\n" + "When you're satisfied, please approve at " + ARGV[2].to_s

elsif ARGV[0].to_s == "-f"

    start_msg="@team" + "\n" + ":parrotdad:" + " Build has failed! " + ":parrotdad:" + "\n" + "\n" + ARGV[1]

elsif ARGV[0].to_s == "-a"

    start_msg="@team" + "\n" + ":dealwithitparrot:" + " Build has been aborted! " + ":dealwithitparrot:" + "\n" + "\n" + ARGV[1]

end


api_token_client.chat_message(flow: '', content: start_msg)
